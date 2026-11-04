import io
p = 'C:\\Users\\Admin\\Documents\\Fent\\net\\silentclient\\fent\\mixin\\FentCosmeticTypeMixin.java'
s = io.open(p, encoding='utf-8', newline='').read()

old = (
    "      String saved = null;\n"
    "      try {\n"
    "         saved = net.silentclient.internal.aq.p.b(type);\n"
    "      } catch (Throwable ignored) {\n"
    "      }\n"
    "      String value = saved == null ? \"\" : saved;\n"
    "      if (value.isEmpty() && id != null && !id.isEmpty()) {\n"
    "         value = id;\n"
    "      }\n"
    "      if (!value.isEmpty() && !idOk(value)) {\n"
    "         return;\n"
    "      }\n"
    "      fent$save(type, value);\n"
)
new = (
    "      String value;\n"
    "      if (id != null && !id.isEmpty()) {\n"
    "         // The id being equipped is authoritative; prefer it over any stale\n"
    "         // read-back mirror value so a fresh locker pick is never clobbered\n"
    "         // before it gets published to peers.\n"
    "         value = id;\n"
    "      } else {\n"
    "         String saved = null;\n"
    "         try {\n"
    "            saved = net.silentclient.internal.aq.p.b(type);\n"
    "         } catch (Throwable ignored) {\n"
    "         }\n"
    "         value = saved == null ? \"\" : saved;\n"
    "      }\n"
    "      if (!value.isEmpty() && !idOk(value)) {\n"
    "         return;\n"
    "      }\n"
    "      fent$save(type, value);\n"
)
count = s.count(old)
assert count == 1, ("count", count)
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print("OK replaced in Fent too")
