import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mechanical proof that every mixin target resolves against a runtime jar.
 *
 * Parses each mixin source for @Mixin targets, @Shadow fields/methods and
 * @Inject method descriptors, then asserts each exists via javap. Any drift
 * in SilentClient's obfuscated mappings fails LOUDLY here instead of
 * silently killing the whole mixin in-game (one bad shadow/inject target
 * rejects the entire mixin class, which is how fent-to-fent sync died
 * without a single headless test noticing).
 *
 * Usage: MixinTargetCheck <runtime.jar> <mixin-src-dir> [mixins.json]
 * Exit 0 when every ACTIVE mixin (listed in mixins.fent.json client[])
 * resolves; exit 1 otherwise. Disabled mixins are reported, not failed.
 */
public final class MixinTargetCheck {
   static int failures;
   static int checked;

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         System.out.println("Usage: MixinTargetCheck <runtime.jar> <mixin-src-dir> [mixins.json]");
         System.exit(2);
      }
      String runtime = args[0];
      Path dir = Paths.get(args[1]);
      Set<String> active = new HashSet<String>();
      if (args.length >= 3) {
         String json = new String(Files.readAllBytes(Paths.get(args[2])), "UTF-8");
         Matcher m = Pattern.compile("\"([A-Za-z0-9]+Mixin)\"").matcher(json);
         while (m.find()) {
            active.add(m.group(1));
         }
      }
      List<Path> files = new ArrayList<Path>();
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.java")) {
         for (Path p : ds) {
            files.add(p);
         }
      }
      for (Path file : files) {
         checkMixin(runtime, file, active.isEmpty() || active.contains(base(file)));
      }
      System.out.println("checked=" + checked + " failures=" + failures);
      System.exit(failures == 0 ? 0 : 1);
   }

   static String base(Path file) {
      String name = file.getFileName().toString();
      return name.substring(0, name.length() - 5);
   }

   static void checkMixin(String runtime, Path file, boolean active) throws Exception {
      String src = new String(Files.readAllBytes(file), "UTF-8");
      String name = base(file);
      List<String> targets = new ArrayList<String>();
      // @Mixin(targets = "a.b.C"), @Mixin(targets = {"a","b"}), @Mixin(Foo.class)
      Matcher tm = Pattern.compile("@Mixin\\((?:targets\\s*=\\s*)?(?:\\{([^}]*)\\}|\"([^\"]+)\")").matcher(src);
      while (tm.find()) {
         if (tm.group(2) != null) {
            targets.add(tm.group(2));
         } else if (tm.group(1) != null) {
            Matcher q = Pattern.compile("\"([^\"]+)\"").matcher(tm.group(1));
            while (q.find()) {
               targets.add(q.group(1));
            }
         }
      }
      if (targets.isEmpty()) {
         System.out.println("SKIP " + name + ": no targets found");
         return;
      }
      Map<String, String> imports = new HashMap<String, String>();
      Matcher im = Pattern.compile("import\\s+([\\w.$]+);").matcher(src);
      while (im.find()) {
         String fqn = im.group(1);
         int dot = fqn.lastIndexOf('.');
         if (dot > 0 && !fqn.endsWith(".*")) {
            imports.put(fqn.substring(dot + 1), fqn);
         }
      }
      // @Mixin(Foo.class) form: resolve the simple name through imports
      Matcher cm = Pattern.compile("@Mixin\\(\\s*([\\w.$]+)\\.class\\s*\\)").matcher(src);
      while (cm.find()) {
         String cls = cm.group(1);
         if (!cls.contains(".") && imports.containsKey(cls)) {
            cls = imports.get(cls);
         }
         if (!targets.contains(cls)) {
            targets.add(cls);
         }
      }
      for (String target : targets) {
         String dotted = target.replace('/', '.').replace('$', '.');
         List<String> members;
         try {
            members = javap(runtime, target);
         } catch (Exception e) {
            report(active, "FAIL " + name + ": target class missing: " + target);
            continue;
         }
         checked++;
         if (members.isEmpty()) {
            report(active, "FAIL " + name + ": no members listed for " + target);
            continue;
         }
         System.out.println("ok   " + name + " -> " + dotted);
         checkShadows(active, name, target, src, imports, members);
         checkInjects(active, name, target, src, members);
      }
   }

   static void checkShadows(boolean active, String name, String target,
         String src, Map<String, String> imports, List<String> members) {
      // @Shadow <type...> <fieldname>;
      Matcher fm = Pattern.compile("@Shadow\\s+(?:@\\w+\\s*)*(?:[\\w.$<>\\[\\], ?]+\\s+)(\\w+)\\s*;")
         .matcher(src);
      while (fm.find()) {
         String field = fm.group(1);
         if (field.equals("class")) {
            continue;
         }
         String decl = fm.group(0);
         if (decl.contains("(")) {
            continue; // method, handled below
         }
         checked++;
         boolean found = false;
         for (String member : members) {
            if (member.matches(".*[\\s.]" + Pattern.quote(field) + "\\s*;.*")) {
               found = true;
               break;
            }
         }
         if (found) {
            System.out.println("ok   " + name + " shadow field " + field);
         } else {
            report(active, "FAIL " + name + ": shadow field missing on target: " + field);
         }
      }
      // @Shadow [modifiers...] <type> <name>(params); - modifiers allowed
      Matcher mm = Pattern.compile("@Shadow\\s+(?:@\\w+(?:\\([^)]*\\))?\\s*)*"
         + "(?:\\w+\\s+)*([\\w.$<>\\[\\]]+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*;").matcher(src);
      while (mm.find()) {
         String method = mm.group(2);
         String params = mm.group(3).trim();
         List<String> want = new ArrayList<String>();
         if (!params.isEmpty()) {
            for (String p : splitTopLevel(params)) {
               want.add(resolveType(p.trim(), imports));
            }
         }
         checked++;
         boolean found = false;
         for (String member : members) {
            Parsed parsed = parseJavapMember(member);
            if (parsed != null && parsed.name.equals(method) && parsed.params.equals(want)) {
               found = true;
               break;
            }
         }
         if (found) {
            System.out.println("ok   " + name + " shadow " + method + paramsSig(want));
         } else {
            report(active, "FAIL " + name + ": shadow method missing on target: "
               + method + paramsSig(want));
         }
      }
   }

   static void checkInjects(boolean active, String name, String target,
         String src, List<String> members) {
      Matcher m = Pattern.compile("method\\s*=\\s*\"([^\"]+)\"").matcher(src);
      Set<String> seen = new HashSet<String>();
      while (m.find()) {
         String desc = m.group(1);
         if (!seen.add(desc)) {
            continue;
         }
         checked++;
         if (desc.equals("<init>") || desc.equals("<clinit>")) {
            boolean found = false;
            if (desc.equals("<clinit>")) {
               for (String member : members) {
                  if (member.contains("static {}") || member.contains("static {};")) {
                     found = true;
                     break;
                  }
               }
               // javap -p omits <clinit> unless -c; accept target presence instead
               found = true;
            } else {
               String simple = target.replace('/', '.');
               int lastDot = simple.lastIndexOf('.');
               String ctor = lastDot >= 0 ? simple.substring(lastDot + 1) : simple;
               int dollar = ctor.lastIndexOf('$');
               if (dollar >= 0) {
                  ctor = ctor.substring(dollar + 1);
               }
               for (String member : members) {
                  if (member.contains(ctor + "(")) {
                     found = true;
                     break;
                  }
               }
            }
            if (found) {
               System.out.println("ok   " + name + " inject " + desc);
            } else {
               report(active, "FAIL " + name + ": inject " + desc + " missing on target");
            }
            continue;
         }
         Parsed want = parseDescriptor(desc);
         if (want == null) {
            report(active, "FAIL " + name + ": cannot parse inject descriptor: " + desc);
            continue;
         }
         boolean found = false;
         for (String member : members) {
            Parsed parsed = parseJavapMember(member);
            if (parsed != null && parsed.name.equals(want.name) && parsed.params.equals(want.params)) {
               found = true;
               break;
            }
         }
         if (found) {
            System.out.println("ok   " + name + " inject " + want.name + paramsSig(want.params));
         } else {
            report(active, "FAIL " + name + ": inject target missing: " + desc);
         }
      }
   }

   static final class Parsed {
      String name;
      List<String> params = new ArrayList<String>();
   }

   /** Splits a comma list, ignoring commas inside angle brackets. */
   static List<String> splitTopLevel(String inside) {
      List<String> parts = new ArrayList<String>();
      int depth = 0;
      StringBuilder cur = new StringBuilder();
      for (int i = 0; i < inside.length(); i++) {
         char c = inside.charAt(i);
         if (c == '<') {
            depth++;
         } else if (c == '>') {
            depth--;
         }
         if (c == ',' && depth == 0) {
            parts.add(cur.toString());
            cur.setLength(0);
         } else {
            cur.append(c);
         }
      }
      parts.add(cur.toString());
      return parts;
   }

   /** Parses a javap -p member line into name + dotted param types. */
   static Parsed parseJavapMember(String line) {
      int open = line.indexOf('(');
      int close = line.lastIndexOf(')');
      if (open < 0 || close < open) {
         return null;
      }
      String before = line.substring(0, open).trim();
      int sp = before.lastIndexOf(' ');
      if (sp < 0) {
         return null;
      }
      Parsed parsed = new Parsed();
      parsed.name = before.substring(sp + 1);
      String inside = line.substring(open + 1, close).trim();
      if (!inside.isEmpty()) {
         for (String p : splitTopLevel(inside)) {
            String t = p.trim().replaceAll("\\s+final\\s+", " ").trim();
            // strip generics noise, keep raw dotted type
            t = t.replaceAll("<.+>", "").trim();
            if (t.startsWith("final ")) {
               t = t.substring(6);
            }
            parsed.params.add(t);
         }
      }
      return parsed;
   }

   /** Parses an @Inject method descriptor like a(Lpkg/Cls;Ljava/lang/String;)Z. */
   static Parsed parseDescriptor(String desc) {
      int open = desc.indexOf('(');
      int close = desc.indexOf(')');
      if (open < 0 || close < open) {
         return null;
      }
      Parsed parsed = new Parsed();
      parsed.name = desc.substring(0, open);
      String inside = desc.substring(open + 1, close);
      int i = 0;
      while (i < inside.length()) {
         char c = inside.charAt(i);
         if (c == 'L') {
            int semi = inside.indexOf(';', i);
            if (semi < 0) {
               return null;
            }
            parsed.params.add(inside.substring(i + 1, semi).replace('/', '.').replace('$', '.'));
            i = semi + 1;
         } else if (c == '[') {
            int j = i;
            while (j < inside.length() && inside.charAt(j) == '[') {
               j++;
            }
            if (j < inside.length() && inside.charAt(j) == 'L') {
               int semi = inside.indexOf(';', j);
               if (semi < 0) {
                  return null;
               }
               parsed.params.add(inside.substring(j + 1, semi).replace('/', '.') + "[]");
               i = semi + 1;
            } else {
               parsed.params.add(primitive(inside.charAt(j)) + "[]");
               i = j + 1;
            }
         } else {
            parsed.params.add(primitive(c));
            i++;
         }
      }
      return parsed;
   }

   static String primitive(char c) {
      switch (c) {
         case 'Z': return "boolean";
         case 'B': return "byte";
         case 'C': return "char";
         case 'D': return "double";
         case 'F': return "float";
         case 'I': return "int";
         case 'J': return "long";
         case 'S': return "short";
         case 'V': return "void";
         default: return String.valueOf(c);
      }
   }

   static String resolveType(String type, Map<String, String> imports) {
      String t = type.replaceAll("<.+>", "").trim();
      // strip the parameter name: "CosmeticType type" -> "CosmeticType"
      int sp = t.indexOf(' ');
      if (sp > 0) {
         t = t.substring(0, sp);
      }
      boolean array = t.endsWith("[]");
      if (array) {
         t = t.substring(0, t.length() - 2);
      }
      String resolved;
      if (t.contains(".")) {
         resolved = t;
      } else if (t.length() == 1 && primitive(t.charAt(0)).equals(t)) {
         resolved = t;
      } else if (imports.containsKey(t)) {
         resolved = imports.get(t);
      } else {
         resolved = "java.lang." + t;
      }
      return array ? resolved + "[]" : resolved;
   }

   static String paramsSig(List<String> params) {
      StringBuilder sb = new StringBuilder("(");
      for (int i = 0; i < params.size(); i++) {
         if (i > 0) {
            sb.append(',');
         }
         sb.append(params.get(i));
      }
      return sb.append(')').toString();
   }

   static void report(boolean active, String message) {
      System.out.println(message);
      if (active) {
         failures++;
      }
   }

<<<<<<< HEAD
   static String javapExe() {
      String home = System.getProperty("java.home", "");
      if (!home.isEmpty()) {
         Path candidate = Paths.get(home).resolve("bin")
            .resolve(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "javap.exe" : "javap");
         if (Files.exists(candidate)) {
            return candidate.toString();
         }
      }
      return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "javap.exe" : "javap";
   }

=======
>>>>>>> 8b0d7c8da7408ff67c0e2647985d130930e96154
   static List<String> javap(String runtime, String target) throws Exception {
      // javap wants dots; targets in this codebase use dots (I$e style ok).
      String cls = target.replace('/', '.');
      Process process = new ProcessBuilder(
<<<<<<< HEAD
         javapExe(),
=======
         "C:\\Users\\Admin\\.opt\\jdk21\\jdk-21.0.12.1+1\\bin\\javap.exe",
>>>>>>> 8b0d7c8da7408ff67c0e2647985d130930e96154
         "-cp", runtime, "-p", cls).redirectErrorStream(true).start();
      BufferedReader reader = new BufferedReader(
         new InputStreamReader(process.getInputStream(), "UTF-8"));
      List<String> lines = new ArrayList<String>();
      String line;
      boolean missing = false;
      while ((line = reader.readLine()) != null) {
         if (line.startsWith("Error: class not found")) {
            missing = true;
         }
         lines.add(line.trim());
      }
      process.waitFor();
      if (missing) {
         throw new IllegalStateException("class not found: " + cls);
      }
      return lines;
   }
}
