package com.chobotok.voicemetronome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String[] VOICES = {"helio", "aria", "magnus", "clicks"};
    private static final String[] NUMS =
            {"one", "two", "three", "four", "five", "six", "seven", "eight"};
    private static final String[] CLICK_SOUNDS = {"classic", "wood", "digital", "cowbell"};
    /** Asset file per click sound; "classic" keeps the original hi/lo pair. */
    private static final String[] CLICK_FILES = {null, "wood", "digital", "cowbell"};

    private static final int MIN_BPM = 30;
    private static final int MAX_BPM = 240;

    private SoundPool soundPool;
    private final Map<String, Map<String, Integer>> sounds = new HashMap<>();

    private int bpm = 120;
    private int beatsPerBar = 4;
    private int voiceIndex = 0;
    private boolean running = false;
    private boolean eighthMode = false;
    private int clickSoundIndex = 0;
    private boolean accentLast = false;
    private boolean andIsClick = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long nextBeatTime;
    private int currentBeat;
    private int currentStep; // eighth-note slots: 0..beatsPerBar*2-1
    private final List<Long> taps = new ArrayList<>();

    private TextView beatNumber;
    private TextView bpmLabel;
    private TextView beatsLabel;
    private SeekBar bpmSeek;
    private LinearLayout dotsRow;
    private Button startStop;
    private Button[] voiceButtons;
    private Button countQuarter;
    private Button countEighth;
    private Button[] clickButtons;
    private Button accentFirst;
    private Button accentLastBtn;
    private LinearLayout clicksOptions;
    private Button andVoice;
    private Button andClick;
    private LinearLayout andOptions;

    private final Runnable beatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (eighthMode) {
                // "1 and 2 and…": number on the beat, "and" exactly halfway
                int beat = currentStep / 2;
                boolean isAnd = (currentStep % 2) == 1;
                playEighth(beat, isAnd);
                updateEighthUi(beat, isAnd);
                currentStep = (currentStep + 1) % (beatsPerBar * 2);
                nextBeatTime += 30000L / bpm;
            } else {
                playBeat(currentBeat);
                updateBeatUi(currentBeat);
                currentBeat = (currentBeat + 1) % beatsPerBar;
                nextBeatTime += 60000L / bpm;
            }
            long delay = nextBeatTime - SystemClock.uptimeMillis();
            handler.postDelayed(this, Math.max(0, delay));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        beatNumber = findViewById(R.id.beatNumber);
        bpmLabel = findViewById(R.id.bpmLabel);
        beatsLabel = findViewById(R.id.beatsLabel);
        bpmSeek = findViewById(R.id.bpmSeek);
        dotsRow = findViewById(R.id.dotsRow);
        startStop = findViewById(R.id.startStop);
        voiceButtons = new Button[]{
                findViewById(R.id.voice0),
                findViewById(R.id.voice1),
                findViewById(R.id.voice2),
                findViewById(R.id.voice3)};

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attrs)
                .setMaxStreams(8)
                .build();

        // Load all voice clips in the background.
        startStop.setEnabled(false);
        startStop.setText("LOADING…");
        new Thread(this::loadSounds).start();

        bpmSeek.setMax(MAX_BPM - MIN_BPM);
        bpmSeek.setProgress(bpm - MIN_BPM);
        bpmSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                setBpm(MIN_BPM + p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        findViewById(R.id.bpmMinus).setOnClickListener(v -> setBpm(bpm - 1));
        findViewById(R.id.bpmPlus).setOnClickListener(v -> setBpm(bpm + 1));
        findViewById(R.id.tapButton).setOnClickListener(v -> onTap());

        findViewById(R.id.beatsMinus).setOnClickListener(v -> setBeatsPerBar(beatsPerBar - 1));
        findViewById(R.id.beatsPlus).setOnClickListener(v -> setBeatsPerBar(beatsPerBar + 1));

        for (int i = 0; i < voiceButtons.length; i++) {
            final int idx = i;
            voiceButtons[i].setOnClickListener(v -> setVoice(idx));
        }

        countQuarter = findViewById(R.id.countQuarter);
        countEighth = findViewById(R.id.countEighth);
        countQuarter.setOnClickListener(v -> setEighthMode(false));
        countEighth.setOnClickListener(v -> setEighthMode(true));

        clicksOptions = findViewById(R.id.clicksOptions);
        clickButtons = new Button[]{
                findViewById(R.id.click0),
                findViewById(R.id.click1),
                findViewById(R.id.click2),
                findViewById(R.id.click3)};
        for (int i = 0; i < clickButtons.length; i++) {
            final int idx = i;
            clickButtons[i].setOnClickListener(v -> setClickSound(idx));
        }
        accentFirst = findViewById(R.id.accentFirst);
        accentLastBtn = findViewById(R.id.accentLast);
        accentFirst.setOnClickListener(v -> setAccentLast(false));
        accentLastBtn.setOnClickListener(v -> setAccentLast(true));

        andOptions = findViewById(R.id.andOptions);
        andVoice = findViewById(R.id.andVoice);
        andClick = findViewById(R.id.andClick);
        andVoice.setOnClickListener(v -> setAndIsClick(false));
        andClick.setOnClickListener(v -> setAndIsClick(true));

        findViewById(R.id.appTitle).setOnClickListener(v -> showAbout());

        startStop.setOnClickListener(v -> {
            if (running) stopMetro(); else startMetro();
        });

        setVoice(0);
        setClickSound(0);
        setAccentLast(false);
        setAndIsClick(false);
        setEighthMode(false);
        rebuildDots();
        updateBpmLabel();
    }

    /** Short About popup: what the app does, and who wrote it. */
    private void showAbout() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        new AlertDialog.Builder(this)
                .setTitle("Voice Metronome " + version)
                .setMessage("Counts the beat out loud in a human voice — 1 2 3 4, " +
                        "or 1 & 2 & when you switch on eighth notes. Made for young " +
                        "musicians learning to feel the beat.\n\nWritten by Yuriy Chobotok")
                .setPositiveButton("OK", null)
                .show();
    }

    private void loadSounds() {
        for (String voice : VOICES) {
            Map<String, Integer> map = new HashMap<>();
            if ("clicks".equals(voice)) {
                map.put("hi", loadClip(voice, "hi", ".wav"));
                map.put("lo", loadClip(voice, "lo", ".wav"));
                map.put("wood", loadClip(voice, "wood", ".wav"));
                map.put("digital", loadClip(voice, "digital", ".wav"));
                map.put("cowbell", loadClip(voice, "cowbell", ".wav"));
            } else {
                for (String n : NUMS) {
                    map.put(n, loadClip(voice, n, ".mp3"));
                }
                map.put("and", loadClip(voice, "and", ".mp3"));
            }
            sounds.put(voice, map);
        }
        runOnUiThread(() -> {
            startStop.setEnabled(true);
            startStop.setText("START");
        });
    }

    private int loadClip(String voice, String clip, String ext) {
        try {
            AssetFileDescriptor afd =
                    getAssets().openFd("voices/" + voice + "/" + clip + ext);
            int id = soundPool.load(afd, 1);
            afd.close();
            return id;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isClicks() {
        return "clicks".equals(VOICES[voiceIndex]);
    }

    /** Which beat carries the stress: first of the bar, or last. */
    private boolean isAccent(int beat) {
        return accentLast ? beat == beatsPerBar - 1 : beat == 0;
    }

    private void playBeat(int beat) {
        Map<String, Integer> map = sounds.get(VOICES[voiceIndex]);
        if (map == null) return;
        if (isClicks()) {
            playClick(map, isAccent(beat), false);
            return;
        }
        String clip = NUMS[beat];
        Integer id = map.get(clip);
        if (id == null || id == 0) return;
        float vol = isAccent(beat) ? 1.0f : 0.72f;
        soundPool.play(id, vol, vol, 1, 0, 1.0f);
    }

    /** One click tick. Accented beats ring brighter; "and" ticks stay soft. */
    private void playClick(Map<String, Integer> map, boolean accented, boolean isAnd) {
        Integer id;
        float rate = 1.0f;
        float vol;
        if (clickSoundIndex == 0) {
            // classic: the original hi/lo pair
            id = map.get(accented ? "hi" : "lo");
            vol = isAnd ? 0.35f : (accented ? 1.0f : 0.8f);
        } else {
            id = map.get(CLICK_FILES[clickSoundIndex]);
            vol = isAnd ? 0.35f : (accented ? 1.0f : 0.75f);
            if (accented) rate = 1.2f; // accent rings a touch brighter
        }
        if (id == null || id == 0) return;
        soundPool.play(id, vol, vol, 1, 0, rate);
    }

    /**
     * One eighth-note slot. Numbers land on the beat, the "and" lands exactly
     * halfway to the next beat — the way eighth notes are counted.
     */
    private void playEighth(int beat, boolean isAnd) {
        Map<String, Integer> map = sounds.get(VOICES[voiceIndex]);
        if (map == null) return;
        if (isClicks()) {
            playClick(map, isAccent(beat), isAnd);
            return;
        }
        if (isAnd && andIsClick) {
            // "one"-click-"two"-click: the off-beat ticks with the chosen click sound
            Map<String, Integer> cmap = sounds.get("clicks");
            if (cmap != null) {
                playClick(cmap, false, true);
                return;
            }
        }
        String clip = isAnd ? "and" : NUMS[beat];
        Integer id = map.get(clip);
        if (id == null || id == 0) return;
        // the "and" stays a touch softer so the beat remains the anchor
        float vol = isAnd ? 0.6f : (isAccent(beat) ? 1.0f : 0.72f);
        soundPool.play(id, vol, vol, 1, 0, 1.0f);
    }

    private void startMetro() {
        running = true;
        currentBeat = 0;
        currentStep = 0;
        nextBeatTime = SystemClock.uptimeMillis();
        handler.post(beatRunnable);
        startStop.setText("STOP");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stopMetro() {
        running = false;
        handler.removeCallbacks(beatRunnable);
        startStop.setText("START");
        beatNumber.setText("–");
        clearDots();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateBeatUi(int beat) {
        beatNumber.setText(String.valueOf(beat + 1));
        beatNumber.setTextColor(isAccent(beat) ? getColor(R.color.cozy_accent)
                : getColor(R.color.cozy_ink));
        pulseBeatNumber();
        for (int i = 0; i < dotsRow.getChildCount(); i++) {
            TextView dot = (TextView) dotsRow.getChildAt(i);
            dot.setTextColor(i == beat ? getColor(R.color.cozy_accent)
                    : getColor(R.color.cozy_dot_off));
        }
    }

    /**
     * Eighth-note display for young players: the number on the beat,
     * a soft blue "&" on the "and". The beat's dot stays lit through
     * both, dimmer on the "and".
     */
    private void updateEighthUi(int beat, boolean isAnd) {
        if (isAnd) {
            beatNumber.setText("&");
            beatNumber.setTextColor(getColor(R.color.cozy_and));
        } else {
            beatNumber.setText(String.valueOf(beat + 1));
            beatNumber.setTextColor(isAccent(beat) ? getColor(R.color.cozy_accent)
                    : getColor(R.color.cozy_ink));
        }
        pulseBeatNumber();
        for (int i = 0; i < dotsRow.getChildCount(); i++) {
            TextView dot = (TextView) dotsRow.getChildAt(i);
            dot.setTextColor(i == beat
                    ? getColor(isAnd ? R.color.cozy_and_dot : R.color.cozy_accent)
                    : getColor(R.color.cozy_dot_off));
        }
    }

    private void pulseBeatNumber() {
        beatNumber.setScaleX(1.3f);
        beatNumber.setScaleY(1.3f);
        beatNumber.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    private void rebuildDots() {
        dotsRow.removeAllViews();
        for (int i = 0; i < beatsPerBar; i++) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(28);
            dot.setTextColor(getColor(R.color.cozy_dot_off));
            dot.setPadding(10, 0, 10, 0);
            dotsRow.addView(dot);
        }
    }

    private void clearDots() {
        for (int i = 0; i < dotsRow.getChildCount(); i++) {
            ((TextView) dotsRow.getChildAt(i))
                    .setTextColor(getColor(R.color.cozy_dot_off));
        }
    }

    private void setBpm(int value) {
        bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, value));
        updateBpmLabel();
        if (bpmSeek.getProgress() != bpm - MIN_BPM) {
            bpmSeek.setProgress(bpm - MIN_BPM);
        }
    }

    private void updateBpmLabel() {
        bpmLabel.setText(bpm + " BPM");
    }

    private void setBeatsPerBar(int value) {
        beatsPerBar = Math.max(2, Math.min(8, value));
        beatsLabel.setText(String.valueOf(beatsPerBar));
        rebuildDots();
        if (running) {
            // restart the bar cleanly on the next beat
            currentBeat = 0;
            currentStep = 0;
        }
    }

    /** Quarter-note counting ("1 2 3 4") vs eighth-note counting ("1 & 2 &"). */
    private void setEighthMode(boolean eighth) {
        eighthMode = eighth;
        int on = getColor(R.color.cozy_accent);
        int off = getColor(R.color.cozy_muted);
        countQuarter.setTextColor(eighth ? off : on);
        countEighth.setTextColor(eighth ? on : off);
        andOptions.setVisibility(eighth ? LinearLayout.VISIBLE : LinearLayout.GONE);
        if (running) {
            currentBeat = 0;
            currentStep = 0;
        }
    }

    /** What the off-beat sounds like in eighth-note mode: voice "and" or a click. */
    private void setAndIsClick(boolean click) {
        andIsClick = click;
        int on = getColor(R.color.cozy_accent);
        int off = getColor(R.color.cozy_muted);
        andVoice.setTextColor(click ? off : on);
        andClick.setTextColor(click ? on : off);
    }

    private void setVoice(int idx) {
        voiceIndex = idx;
        int on = getColor(R.color.cozy_accent);
        int off = getColor(R.color.cozy_muted);
        for (int i = 0; i < voiceButtons.length; i++) {
            voiceButtons[i].setTextColor(i == idx ? on : off);
        }
        clicksOptions.setVisibility(isClicks() ? LinearLayout.VISIBLE : LinearLayout.GONE);
    }

    private void setClickSound(int idx) {
        clickSoundIndex = idx;
        int on = getColor(R.color.cozy_accent);
        int off = getColor(R.color.cozy_muted);
        for (int i = 0; i < clickButtons.length; i++) {
            clickButtons[i].setTextColor(i == idx ? on : off);
        }
    }

    private void setAccentLast(boolean last) {
        accentLast = last;
        int on = getColor(R.color.cozy_accent);
        int off = getColor(R.color.cozy_muted);
        accentFirst.setTextColor(last ? off : on);
        accentLastBtn.setTextColor(last ? on : off);
    }

    private void onTap() {
        long now = SystemClock.uptimeMillis();
        if (!taps.isEmpty() && now - taps.get(taps.size() - 1) > 2000) {
            taps.clear();
        }
        taps.add(now);
        if (taps.size() > 6) taps.remove(0);
        if (taps.size() >= 2) {
            long sum = 0;
            for (int i = 1; i < taps.size(); i++) {
                sum += taps.get(i) - taps.get(i - 1);
            }
            long avg = sum / (taps.size() - 1);
            if (avg > 0) setBpm((int) Math.round(60000.0 / avg));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) stopMetro();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(beatRunnable);
        if (soundPool != null) {
            soundPool.release();
        }
    }
}
