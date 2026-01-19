package dev.shared.cudoriver.task;

import eu.darkbot.api.API;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.utils.Inject;

@Feature(name = "R01 Dispatch Module", description = "Automatically collects and starts R01 dispatch tasks")
public class R01SevkiyatTask implements Task {

    private final BackpageAPI backpage;
    private long lastActionTime = 0;
    private int currentSlot = 1;
    private boolean collecting = true;

    // Ayarların: 4 slot ve 3 saat bekleme
    private static final int FIRST_SLOT_ID = 1;
    private static final int LAST_SLOT_ID = 4;
    private static final long ACTION_DELAY = 10800000L;
    private static final int R01_RETRIEVER_ID = 1;

    @Inject
    public R01SevkiyatTask(API api) {
        // Hata veren metotları tamamen baypas ediyoruz
        // Doğrudan API nesnesinden BackpageAPI'yi istiyoruz.
        this.backpage = (BackpageAPI) api;
    }

    @Override
    public void onTickTask() {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;
        if (backpage == null) return;

        try {
            if (collecting) {
                backpage.getConnection("indexInternal.es?action=internalDispatch&subaction=collect&slotId=" + currentSlot);
            } else {
                backpage.getConnection("indexInternal.es?action=internalDispatch&subaction=init&retrieverId=" + R01_RETRIEVER_ID + "&slotId=" + currentSlot);
            }
            updateState();
        } catch (Exception e) {
            // Hataları sessizce yut
        }
    }

    private void updateState() {
        lastActionTime = System.currentTimeMillis();
        currentSlot++;

        if (currentSlot > LAST_SLOT_ID) {
            currentSlot = FIRST_SLOT_ID;
            collecting = !collecting;
        }
    }
}