import io
p = 'C:\\Users\\Admin\\Documents\\Fent/net/silentclient/fent/FentShare.java'
s = io.open(p, encoding='utf-8').read()

old = """         thread.setDaemon(true);
         applierThread = thread;
         thread.start();
      } catch (Throwable ignored) {
      }
   }
"""
new = """         thread.setDaemon(true);
         applierThread = thread;
         thread.start();
         startCosmeticWatch();
      } catch (Throwable ignored) {
      }
   }

   /**
    * In-game realtime check: the sync api is polled (and applied) every second
    * for cosmetic changes, alongside the SSE push. This guarantees that any
    * cosmetic change made in a server or world is picked up within ~1s even when
    * the SSE stream hiccups or pushes lag behind.
    */
   private static void startCosmeticWatch() {
      try {
         Thread thread = new Thread(new Runnable() {
            public void run() {
               while (true) {
                  try { Thread.sleep(1000L); } catch (InterruptedException ignored) { return; }
                  if (!apiEnabled()) { continue; }
                  apiPull();
               }
            }
         }, "Fent-Share-CosmeticWatch");
         thread.setDaemon(true);
         thread.start();
      } catch (Throwable ignored) {
      }
   }
"""
assert s.count(old) == 1, ("count", s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8').write(s)
print("OK - cosmetic watch added")
