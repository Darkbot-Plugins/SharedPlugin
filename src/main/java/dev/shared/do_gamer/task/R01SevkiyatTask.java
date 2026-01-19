package dev.shared.do_gamer.task;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.BackpageAPI;
import java.util.concurrent.TimeUnit;

@Feature(name = "R-01 Sevkiyat Otomasyonu", description = "3 Saatte bir R-01 toplayıp tekrar gönderir.")
public class R01SevkiyatTask implements Task {

    private final BackpageAPI backpage;
    private long sonKontrolZamani = 0;
    private final long BEKLEME_SURESI = TimeUnit.HOURS.toMillis(3) + TimeUnit.MINUTES.toMillis(1);

    // Darkbot API'sini constructor üzerinden alıyoruz (En güvenli yol)
    public R01SevkiyatTask(PluginAPI api) {
        this.backpage = api.requireAPI(BackpageAPI.class);
    }

    @Override
    public void onTickTask() {
        if (System.currentTimeMillis() - sonKontrolZamani > BEKLEME_SURESI) {
            calistirSevkiyatDongusu();
            sonKontrolZamani = System.currentTimeMillis();
        }
    }

    // Bu metod listede çıktı ama şimdilik boş bırakabiliriz
    @Override
    public void onBackgroundTick() {
    }

    private void calistirSevkiyatDongusu() {
        new Thread(() -> {
            try {
                System.out.println("Sevkiyat döngüsü başlatılıyor...");

                // 1. Adım: Topla
                for (int slot = 1; slot <= 4; slot++) {
                    backpage.getConnection("indexInternal.es?action=internalDispatch&subaction=collect&slotId=" + slot);
                    Thread.sleep(1000);
                }

                // 2. Adım: R-01 Gönder
                for (int slot = 1; slot <= 4; slot++) {
                    backpage.getConnection("indexInternal.es?action=internalDispatch&subaction=init&retrieverId=1&slotId=" + slot);
                    Thread.sleep(1000);
                }

                System.out.println("Sevkiyat başarıyla tamamlandı.");

            } catch (Exception e) {
                System.err.println("Sevkiyat hatası: " + e.getMessage());
            }
        }).start();
    }
}