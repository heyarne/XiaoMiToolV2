package com.xiaomitool.v2.engine;

import com.xiaomitool.v2.gui.GuiUtils;
import com.xiaomitool.v2.gui.WindowManager;
import com.xiaomitool.v2.gui.controller.LoginController;
import com.xiaomitool.v2.language.Lang;
import com.xiaomitool.v2.logging.Log;
import com.xiaomitool.v2.logging.feedback.LiveFeedbackEasy;
import com.xiaomitool.v2.resources.ResourcesConst;
import com.xiaomitool.v2.resources.ResourcesManager;
import com.xiaomitool.v2.utility.utils.MutexUtils;
import com.xiaomitool.v2.utility.utils.SettingsUtils;
import com.xiaomitool.v2.utility.utils.StrUtils;
import com.xiaomitool.v2.utility.utils.UpdateUtils;
import com.xiaomitool.v2.xiaomi.XiaomiKeystore;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ToolManager {
    public static final String TOOL_VERSION = "9.8.7";
    public static final String URL_DONATION = "https://www.xiaomitool.com/V2/donate";
    public static final String TOOL_VERSION_EX = "alpha";
    public static final String XMT_HOST = "https://www.xiaomitool.com/V2";
    public static final String URL_UPDATE_V2 = XMT_HOST + "/updateV2.php";
    public static final String URL_LATEST = XMT_HOST + "/latest";
    public static final boolean DEBUG_MODE = true;
    private static boolean exiting = false;
    private static List<Stage> activeStages = new ArrayList<>();
    private static String runningInstanceId = null;

    public static String getFeedbackUrl() {
        return XMT_HOST + "/feedback";
    }

    public static void init(Stage primaryStage, String[] args) throws Exception {
        if (UpdateUtils.checkUpdateKillMe(args)) {
            System.exit(0);
            return;
        }
        if (!ResourcesManager.init()) {
            Log.error("Failed to init resources dir");
        }
        Log.init();
        boolean isSingleInstance = MutexUtils.lock();
        if (!isSingleInstance) {
            ToolManager.exit(1);
            return;
        }
        SettingsUtils.load();
        Lang.loadSystemLanguage();
        GuiUtils.init();
        checkLoadSession();
        Log.info("Starting XiaoMiTool V2 " + TOOL_VERSION + " : " + ResourcesConst.getLogString());
        WindowManager.launchMain(primaryStage);
    }

    public static void showStage(Stage stage) {
        if (stage == null) {
            return;
        }
        activeStages.add(stage);
        stage.show();
    }

    public static void closeStage(Stage stage) {
        if (stage == null) {
            return;
        }
        activeStages.remove(stage);
        stage.close();
    }

    public synchronized static void exit(int code) {
        if (exiting) {
            return;
        }
        exiting = true;
        LiveFeedbackEasy.sendClose();
        saveOptions();
        LiveFeedbackEasy.runOnFeedbackSent(() -> {
            for (Stage stage : activeStages) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        stage.close();
                    }
                });
            }
            Log.closeLogFile();
            MutexUtils.unlock();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    ToolManager.closeStage(WindowManager.mainWindow());
                }
            });
            System.exit(code);
        }, Platform.isFxApplicationThread());
    }

    private static void saveOptions() {
        checkSaveSession();
        SettingsUtils.save();
    }

    private static void checkSaveSession() {
        String pcId = XiaomiKeystore.getInstance().getPcId();
        if (pcId != null) {
            SettingsUtils.saveOpt(SettingsUtils.PC_ID, pcId);
        }
        String saveSession = SettingsUtils.getOpt(SettingsUtils.PREF_SAVE_SESSION);
        if (!"true".equals(saveSession)) {
            SettingsUtils.saveOpt(SettingsUtils.SESSION_TOKEN, "null");
            return;
        }
        String json = XiaomiKeystore.getInstance().getJson();
        if (json == null) {
            SettingsUtils.saveOpt(SettingsUtils.SESSION_TOKEN, "null");
            return;
        }
        SettingsUtils.saveOptEncrpyted(SettingsUtils.SESSION_TOKEN, json);
    }

    private static void checkLoadSession() {
        String pcId = SettingsUtils.requirePCId();
        if (pcId != null) {
            XiaomiKeystore.getInstance().setDeviceId(pcId);
        }
        String saveSession = SettingsUtils.getOpt(SettingsUtils.PREF_SAVE_SESSION);
        if (!"true".equals(saveSession)) {
            return;
        }
        String json = SettingsUtils.getOptDecrypted(SettingsUtils.SESSION_TOKEN);
        if (json == null) {
            Log.warn("Failed to load old session token");
            return;
        }
        try {
            JSONObject object = new JSONObject(json);
            XiaomiKeystore.getInstance().setCredentials(object);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    LoginController.setLoginNumber(XiaomiKeystore.getInstance().getUserId());
                }
            });
        } catch (JSONException e) {
            Log.warn("Failed to parse old session token: " + e.getMessage());
        }
    }

    public static String getRunningInstanceId() {
        if (runningInstanceId == null) {
            runningInstanceId = StrUtils.randomWord(16);
        }
        return runningInstanceId;
    }
}
