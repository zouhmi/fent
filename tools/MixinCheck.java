import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class MixinCheck {
   private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
   private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
   private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
   private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
   private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
   private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
   private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
   private static final String MODIFY_VAR = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
   private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
   private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
   private static final String CALLBACK_RETURNABLE =
      "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

   public static void main(String[] args) throws IOException {
      if (args.length < 2) {
         System.err.println("usage: MixinCheck <classpath> <mixinClassFileOrDir> [more...]");
         System.exit(2);
         return;
      }

      List<Path> classpath = new ArrayList<>();
      for (String entry : args[0].split(File.pathSeparator)) {
         if (!entry.trim().isEmpty()) {
            classpath.add(Paths.get(entry.trim()));
         }
      }

      List<Path> mixinClasses = new ArrayList<>();
      for (int i = 1; i < args.length; ++i) {
         Path path = Paths.get(args[i]);
         if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
               stream.filter(candidate -> candidate.toString().endsWith(".class")).sorted().forEach(mixinClasses::add);
            }
         } else {
            mixinClasses.add(path);
         }
      }

      if (mixinClasses.isEmpty()) {
         System.err.println("MixinCheck: no mixin classes found");
         System.exit(2);
         return;
      }

      ClasspathResolver resolver = new ClasspathResolver(classpath);
      int total = 0;
      int broken = 0;

      for (Path mixinClass : mixinClasses) {
         MixinModel model = MixinModel.parse(mixinClass);
         if (model.targets.isEmpty()) {
            System.out.println("MixinCheck: " + model.displayName() + " -> no @Mixin targets, skipped");
            continue;
         }
         System.out.println("MixinCheck: " + model.displayName());
         for (String target : model.targets) {
            Optional<ClassModel> targetClass = resolver.resolve(target);
            if (targetClass.isEmpty()) {
               System.out.println("  MISSING TARGET " + target.replace('/', '.'));
               broken += Math.max(1, model.total());
               continue;
            }
            ClassModel classModel = targetClass.get();
            for (Member member : model.members) {
               ++total;
               if (!classModel.hasMember(member)) {
                  ++broken;
                  System.out.println("  BROKEN field " + member.name + " " + member.descriptor + " (" + member.origin
                     + ") absent from " + classModel.name.replace('/', '.'));
               }
            }
            for (String selector : model.selectors) {
               ++total;
               if (!classModel.hasMethod(selector)) {
                  ++broken;
                  System.out.println("  BROKEN method " + selector + " absent from " + classModel.name.replace('/', '.'));
               }
            }
            for (CallSite site : model.callSites) {
               ++total;
               if (!classModel.hasCall(site.host, site.member)) {
                  ++broken;
                  System.out.println("  BROKEN " + site.describe() + " absent from " + classModel.name.replace('/', '.'));
               }
            }
            for (Injection injection : model.injections) {
               List<String> returns = classModel.returnsOf(injection.selector);
               if (returns.isEmpty()) {
                  continue;
               }
               ++total;
               String wanted = null;
               for (String value : returns) {
                  String kind = "V".equals(value) ? "CallbackInfo" : "CallbackInfoReturnable";
                  wanted = wanted == null || wanted.equals(kind) ? kind : "both";
               }
               String kind = callbackKind(injection.handler);
               if (!wanted.equals(kind)) {
                  ++broken;
                  System.out.println("  BROKEN @Inject " + injection.handler + " takes "
                     + (kind == null || kind.isEmpty() ? "no callback" : kind) + ", " + wanted
                     + " is required by " + classModel.name.replace('/', '.') + "." + injection.selector);
               }
            }
            if (model.total() == 0) {
               System.out.println("  " + target.replace('/', '.') + ": nothing to check");
            }
         }
      }

      if (broken > 0) {
         System.err.println("MixinCheck: FAILED - " + broken + " of " + total + " references do not resolve");
         System.exit(1);
         return;
      }
      System.out.println("MixinCheck: OK - " + total + " references resolve");
   }

   private static final class Member {
      final String name;
      final String descriptor;
      final String origin;

      Member(String name, String descriptor, String origin) {
         this.name = name;
         this.descriptor = descriptor;
         this.origin = origin;
      }
   }

   private static final class Injection {
      final String handler;
      final String selector;

      Injection(String handler, String selector) {
         this.handler = handler;
         this.selector = selector;
      }
   }

   private static String callbackKind(String handler) {
      int open = handler.indexOf('(');
      if (open < 0) {
         return null;
      }
      Type[] arguments = Type.getArgumentTypes(handler.substring(open));
      if (arguments.length == 0) {
         return "";
      }
      String last = arguments[arguments.length - 1].getDescriptor();
      if (CALLBACK_INFO.equals(last)) {
         return "CallbackInfo";
      }
      return CALLBACK_RETURNABLE.equals(last) ? "CallbackInfoReturnable" : "";
   }

   private static final class CallSite {
      final String host;
      final String member;

      CallSite(String host, String member) {
         this.host = host;
         this.member = member;
      }

      String describe() {
         return "[@Redirect " + this.host + "] " + this.member;
      }
   }

   private static final class MixinModel {
      final List<String> targets = new ArrayList<>();
      final List<Member> members = new ArrayList<>();
      final List<String> selectors = new ArrayList<>();
      final List<CallSite> callSites = new ArrayList<>();
      final List<Injection> injections = new ArrayList<>();
      private final String source;

      private MixinModel(String source) {
         this.source = source;
      }

      String displayName() {
         return this.source;
      }

      int total() {
         return this.members.size() + this.selectors.size() + this.callSites.size();
      }

      static MixinModel parse(Path mixinClass) throws IOException {
         MixinModel model = new MixinModel(mixinClass.getFileName().toString());
         new ClassReader(Files.readAllBytes(mixinClass)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
               if (!MIXIN.equals(descriptor)) {
                  return null;
               }
               return new AnnotationVisitor(Opcodes.ASM9) {
                  @Override
                  public AnnotationVisitor visitArray(String name) {
                     if (!"targets".equals(name) && !"value".equals(name)) {
                        return null;
                     }
                     boolean byName = "targets".equals(name);
                     return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String ignored, Object value) {
                           if (byName && value instanceof String) {
                              model.targets.add(((String)value).replace('.', '/'));
                           } else if (value instanceof Type) {
                              model.targets.add(((Type)value).getInternalName());
                           }
                        }
                     };
                  }
               };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
               return new FieldVisitor(Opcodes.ASM9) {
                  @Override
                  public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                     if (SHADOW.equals(annotation)) {
                        model.members.add(new Member(name, descriptor, "@Shadow"));
                     }
                     return null;
                  }
               };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
               String handler = name + descriptor;
               return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                     if (SHADOW.equals(annotation) || OVERWRITE.equals(annotation)) {
                        model.selectors.add(name + descriptor);
                        return null;
                     }
                     if (!INJECT.equals(annotation) && !REDIRECT.equals(annotation)
                        && !MODIFY_ARG.equals(annotation) && !MODIFY_ARGS.equals(annotation)
                        && !MODIFY_VAR.equals(annotation)) {
                        return null;
                     }
                     boolean checkCallback = INJECT.equals(annotation) || REDIRECT.equals(annotation);
                     Pending pending = new Pending(handler, REDIRECT.equals(annotation), checkCallback);
                     return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String key, Object value) {
                           if ("method".equals(key) && value instanceof String) {
                              pending.hosts.add((String)value);
                           }
                        }

                        @Override
                        public AnnotationVisitor visitArray(String key) {
                           if ("method".equals(key)) {
                              return new AnnotationVisitor(Opcodes.ASM9) {
                                 @Override
                                 public void visit(String ignored, Object value) {
                                    if (value instanceof String) {
                                       pending.hosts.add((String)value);
                                    }
                                 }
                              };
                           }
                           if (!"at".equals(key)) {
                              return null;
                           }
                           return new AnnotationVisitor(Opcodes.ASM9) {
                              @Override
                              public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                                 return AT.equals(descriptor) ? MixinModel.atTarget(pending) : null;
                              }
                           };
                        }

                        @Override
                        public AnnotationVisitor visitAnnotation(String key, String descriptor) {
                           return "at".equals(key) && AT.equals(descriptor) ? MixinModel.atTarget(pending) : null;
                        }

                        @Override
                        public void visitEnd() {
                           model.finish(pending);
                        }
                     };
                  }
               };
            }
         }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
         return model;
      }

      static AnnotationVisitor atTarget(Pending pending) {
         return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String key, Object value) {
               if ("target".equals(key) && value instanceof String) {
                  pending.target = (String)value;
               }
            }
         };
      }

      void finish(Pending pending) {
         for (String host : pending.hosts) {
            if (!pending.redirect) {
               this.selectors.add(host);
               if (pending.checkCallback) {
                  this.injections.add(new Injection(pending.handler, host));
               }
               continue;
            }
            if (pending.target == null) {
               continue;
            }
            String member = externalToMember(pending.target);
            if (member != null) {
               this.callSites.add(new CallSite(host, member));
            }
         }
      }

      private static String externalToMember(String target) {
         if (!target.startsWith("L")) {
            return target;
         }
         int typeEnd = target.indexOf(';');
         return typeEnd < 0 ? null : target.substring(1, typeEnd) + "." + target.substring(typeEnd + 1);
      }
   }

   private static final class Pending {
      final String handler;
      final boolean redirect;
      final boolean checkCallback;
      final List<String> hosts = new ArrayList<>();
      String target;

      Pending(String handler, boolean redirect, boolean checkCallback) {
         this.handler = handler;
         this.redirect = redirect;
         this.checkCallback = checkCallback;
      }
   }

   private static final class ClassModel {
      final String name;
      private final Map<String, Set<String>> methods = new HashMap<>();
      private final Map<String, String> fields = new HashMap<>();
      private final Map<String, Set<String>> calls = new HashMap<>();

      ClassModel(String name) {
         this.name = name;
      }

      void addField(String field, String descriptor) {
         this.fields.put(field, descriptor);
      }

      void add(String method, String descriptor) {
         this.methods.computeIfAbsent(method, key -> new HashSet<>()).add(descriptor);
      }

      void addCall(String host, String member) {
         this.calls.computeIfAbsent(host, key -> new HashSet<>()).add(member);
      }

      boolean hasMember(Member member) {
         String descriptor = this.fields.get(member.name);
         return descriptor != null && descriptor.equals(member.descriptor);
      }

      boolean hasMethod(String selector) {
         int open = selector.indexOf('(');
         if (open < 0) {
            return this.methods.containsKey(selector);
         }
         Set<String> descriptors = this.methods.get(selector.substring(0, open));
         return descriptors != null && descriptors.contains(selector.substring(open));
      }

      List<String> returnsOf(String selector) {
         int open = selector.indexOf('(');
         if (open < 0) {
            Set<String> descriptors = this.methods.get(selector);
            if (descriptors == null) {
               return List.of();
            }
            List<String> returns = new ArrayList<>();
            for (String descriptor : descriptors) {
               returns.add(Type.getReturnType(descriptor).getDescriptor());
            }
            return returns;
         }
         Set<String> descriptors = this.methods.get(selector.substring(0, open));
         if (descriptors == null || !descriptors.contains(selector.substring(open))) {
            return List.of();
         }
         return List.of(Type.getReturnType(selector.substring(open)).getDescriptor());
      }

      boolean hasCall(String host, String member) {
         Set<String> direct = this.calls.get(host);
         if (direct != null && direct.contains(member)) {
            return true;
         }
         if (host.indexOf('(') >= 0) {
            return false;
         }
         for (Map.Entry<String, Set<String>> entry : this.calls.entrySet()) {
            if (entry.getKey().startsWith(host + "(") && entry.getValue().contains(member)) {
               return true;
            }
         }
         return false;
      }
   }

   private static final class ClasspathResolver {
      private final List<Path> classpath;
      private final Map<String, Optional<ClassModel>> cache = new HashMap<>();

      ClasspathResolver(List<Path> classpath) {
         this.classpath = classpath;
      }

      Optional<ClassModel> resolve(String internalName) throws IOException {
         Optional<ClassModel> cached = this.cache.get(internalName);
         if (cached != null) {
            return cached;
         }
         Optional<ClassModel> resolved = this.load(internalName);
         this.cache.put(internalName, resolved);
         return resolved;
      }

      private Optional<ClassModel> load(String internalName) throws IOException {
         String resource = internalName + ".class";
         for (Path entry : this.classpath) {
            if (!Files.exists(entry)) {
               continue;
            }
            if (Files.isDirectory(entry)) {
               Path file = entry.resolve(resource);
               if (Files.isRegularFile(file)) {
                  return Optional.of(this.readClass(internalName, Files.readAllBytes(file)));
               }
               continue;
            }
            if (!entry.getFileName().toString().toLowerCase().endsWith(".jar")) {
               continue;
            }
            try (ZipFile zip = new ZipFile(entry.toFile())) {
               ZipEntry zipEntry = zip.getEntry(resource);
               if (zipEntry != null) {
                  try (var stream = zip.getInputStream(zipEntry)) {
                     return Optional.of(this.readClass(internalName, stream.readAllBytes()));
                  }
               }
            }
            catch (IOException ignored) {
            }
         }
         return Optional.empty();
      }

      private ClassModel readClass(String internalName, byte[] bytes) {
         ClassModel model = new ClassModel(internalName);
         new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
               model.addField(name, descriptor);
               return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
               model.add(name, descriptor);
               String host = name + descriptor;
               return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                     model.addCall(host, owner + "." + methodName + methodDescriptor);
                  }

                  @Override
                  public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                     model.addCall(host, owner + "." + fieldName + ":" + fieldDescriptor);
                  }
               };
            }
         }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
         return model;
      }
   }
}
