package com.xiaomitool.v2.engine.actions;

import com.xiaomitool.v2.adb.AdbCommunication;
import com.xiaomitool.v2.adb.device.Device;
import com.xiaomitool.v2.adb.device.DeviceManager;
import com.xiaomitool.v2.engine.CommonsMessages;
import com.xiaomitool.v2.gui.GuiUtils;
import com.xiaomitool.v2.gui.PopupWindow;
import com.xiaomitool.v2.gui.WindowManager;
import com.xiaomitool.v2.gui.controller.SettingsController;
import com.xiaomitool.v2.gui.deviceView.DeviceRecoveryView;
import com.xiaomitool.v2.gui.deviceView.DeviceView;
import com.xiaomitool.v2.gui.drawable.DrawableManager;

import com.xiaomitool.v2.engine.ToolManager;
import com.xiaomitool.v2.gui.raw.RawManager;
import com.xiaomitool.v2.gui.visual.*;
import com.xiaomitool.v2.language.LRes;
import com.xiaomitool.v2.logging.Log;
import com.xiaomitool.v2.logging.feedback.LiveFeedback;
import com.xiaomitool.v2.logging.feedback.LiveFeedbackEasy;
import com.xiaomitool.v2.procedure.install.StockRecoveryInstall;
import com.xiaomitool.v2.resources.ResourcesConst;
import com.xiaomitool.v2.resources.ResourcesManager;
import com.xiaomitool.v2.utility.DriverUtils;
import com.xiaomitool.v2.utility.RunnableMessage;
import com.xiaomitool.v2.utility.WaitSemaphore;
import com.xiaomitool.v2.utility.utils.InetUtils;
import com.xiaomitool.v2.utility.utils.SettingsUtils;
import com.xiaomitool.v2.xiaomi.XiaomiKeystore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.xiaomitool.v2.engine.CommonsMessages.NOOP;
import static com.xiaomitool.v2.engine.actions.ActionsDynamic.SEARCH_SELECT_DEVICES;
import static com.xiaomitool.v2.engine.actions.ActionsDynamic.START_PROCEDURE;


public class ActionsStatic {
    public static RunnableMessage MAIN() {
        return () -> {
            int message;
            message = FIRST_DISCLAIMER().run();
            if (message != 1) {
                ToolManager.exit(0);
            }
            LiveFeedbackEasy.sendOpen(ResourcesConst.getOSLogString(), null);
            Log.info("Discalimer accepted");

            CHECK_FOR_UPDATES().run();
            REQUIRE_REGION().run();
            INSTALL_DRIVERS().run();
            return MOD_CHOOSE_SCREEN().run();
        };
    }
    public static RunnableMessage MOD_CHOOSE_SCREEN(){
        return () -> {
            RunnableMessage nextMain = MAIN_MOD_DEVICE();
            int message = NOOP;
            while (message == NOOP) {
                message = MOD_RECOVER_CHOICE().run();
                Log.debug("Choice: "+message);
                //TODO
                if (message == 1){
                    message = FEATURE_NOT_AVAILABLE().run();
                }
                //ENDTODO
                nextMain = message == 0 ? MAIN_MOD_DEVICE() : MAIN_RECOVER_DEVICE();
            }
            return nextMain.run();
        };
    }

    public static RunnableMessage INSTALL_DRIVERS(){
        return () -> {
            if (!SystemUtils.IS_OS_WINDOWS){
                return 0;
            }
            Log.debug("Installing drivers");
            AdbCommunication.killServer();
            List<Path> driverPath;
            try {
               driverPath = ResourcesManager.getAllInfPaths();
            } catch (IOException e) {
                Log.error("Failed to find inf files: "+e.getMessage());
                return 1;
            }
            int totalFiles = driverPath.size();
            if(totalFiles == 0){
                return 1;
            }

            LoadingAnimation.WithText loadingAnimation = new LoadingAnimation.WithText("",150);
            WindowManager.setMainContent(loadingAnimation, false);
            int i = 1;
            for (Path inf : driverPath){
                String fn = FilenameUtils.getName(inf.toString());
                loadingAnimation.setText(LRes.DRIVER_INSTALLING.toString(fn)+" ("+i+"/"+totalFiles+")");
                Log.info("Installing driver: "+inf);
                boolean result = DriverUtils.installDriver(inf);
                if (result){
                    Log.info("Install driver success");
                } else  {
                    Log.warn("Failed to install driver");
                }
                ++i;
            }
            Thread.sleep(1500);
            Log.info("Refreshing connected devices");
            DriverUtils.refresh();
            WindowManager.removeTopContent();

            return 0;
        };
    }

    public static RunnableMessage MAIN_MOD_DEVICE() { return () -> {
            ActionsDynamic.MAIN_SCREEN_LOADING(LRes.SEARCHING_CONNECTED_DEVICES).run();
            AdbCommunication.restartServer();
            AdbCommunication.registerAutoScanDevices();
            ActionsDynamic.SEARCH_SELECT_DEVICES(null).run();
            Log.debug("SEARCHING");
            ActionsDynamic.REQUIRE_DEVICE_ON(DeviceManager.getSelectedDevice()).run();
            ActionsDynamic.FIND_DEVICE_INFO(DeviceManager.getSelectedDevice()).run();
                return 0;
        };
    }

    public static RunnableMessage FEATURE_NOT_AVAILABLE(){
        return () -> {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    WindowManager.launchPopup(new PopupWindow.ImageTextPopup(LRes.FEATURE_NOT_AVAILABLE.toString(), PopupWindow.Icon.INFO));
                }
            });

            return NOOP;
        };

    }






    public static RunnableMessage RESTART_ADB_SERVER(){
        return () -> {
            Log.info("Restarting adb server");
            ActionsDynamic.MAIN_SCREEN_LOADING(LRes.LOADING).run();
            AdbCommunication.restartServer();
            DeviceManager.refresh();
            WindowManager.removeTopContent();
            return 0;
        };
    }





    public static RunnableMessage MAIN_RECOVER_DEVICE () { return  () -> {
        //TODO
      return MAIN_RECOVER_DEVICE_TMP().run();
    };}

    public static RunnableMessage MAIN_RECOVER_DEVICE_TMP(){
        return  () -> {
            ButtonPane buttonPane = new ButtonPane(LRes.OK_UNDERSTAND);
            Text instruction = new Text(""/*LRes.RECOVERY_RECOVER_TMP.toString()*/);
            instruction.setFont(Font.font(15));
           // instruction.setTextAlignment(TextAlignment.CENTER);
            instruction.setWrappingWidth(WindowManager.getMainPane().getWidth()-180);
            buttonPane.setContent(instruction);
            WindowManager.setMainContent(buttonPane);
            buttonPane.waitClick();
            ActionsDynamic.MAIN_SCREEN_LOADING(LRes.LOADING).run();
            AdbCommunication.restartServer();
            AdbCommunication.registerAutoScanDevices();
            Thread.sleep(500);


            SEARCH_SELECT_DEVICES(Device.Status.SIDELOAD).run();
           // START_PROCEDURE(StockRecoveryInstall.recoverStuckDevice()).run();
            return 0;
        };
    }



    public static RunnableMessage REQUIRE_INTERNET_CONNECTION(){
        return () -> {
            ActionsDynamic.MAIN_SCREEN_LOADING(LRes.LOADING).run();
            Log.info("Checking internet connection");
            boolean connected = InetUtils.isInternetAvailable();
            if (connected){
                return NOOP;
            }
            ErrorPane errorPane = new ErrorPane(LRes.TRY_AGAIN);
            errorPane.setTitle(LRes.INET_CONNECTION_ERROR_TITLE);
            errorPane.setText(LRes.INET_CONNECTION_ERROR_TEXT);

            while (!connected) {
                WindowManager.setMainContent(errorPane,false);
                errorPane.waitClick();
                WindowManager.removeTopContent();
                Thread.sleep(1000);
                connected = InetUtils.isInternetAvailable();

            }

            return NOOP;
        };
    }
    public static RunnableMessage CHECK_FOR_UPDATES(){
        return () -> {
            ActionsStatic.REQUIRE_INTERNET_CONNECTION().run();
            int res = InetUtils.checkForUpdates(ToolManager.URL_UPDATE, ToolManager.TOOL_VERSION, SettingsUtils.requireHashedPCId());
            if (res == 0){
                Log.info("Tool is updated, check version: "+ToolManager.TOOL_VERSION);
                return 0;
            } else if (res != 1){
                Log.warn("Failed to check for updates, error code: "+res);
                return -1;
            }
            ButtonPane buttonPane = new ButtonPane(LRes.IGNORE, LRes.UPDATE_WILL_UPDATE);
            buttonPane.setContentText(LRes.UPDATE_AVAILABLE_TEXT);
            WindowManager.setMainContent(buttonPane,false);
            int msg = buttonPane.waitClick();
            if (msg == 1){
                InetUtils.openUrlInBrowser(ToolManager.URL_LATEST);
                ToolManager.exit(1);
            }
            WindowManager.removeTopContent();
            return 0;
        };
    }


    public static RunnableMessage REQUIRE_REGION(){
        return new RunnableMessage() {
            @Override
            public int run() throws InterruptedException {

                SettingsUtils.Region region = SettingsUtils.getRegion();

                if (region != null){

                    return 0;
                }
                //Log.debug("GET REGION: a");
                SettingsUtils.Region[] regions = SettingsUtils.Region.values();
                ChooserPane.Choice[] choices = new ChooserPane.Choice[regions.length];
                //Log.debug("GET REGION: b");
                for (int i = 0; i<regions.length; ++i){
                    //Log.debug("GET REGION: c");
                    region = regions[i];
                    String hum = region.toHuman(), t = LRes.SELECT_IF_YOURE_FROM.toString(hum);
                    if (SettingsUtils.Region.GLOBAL.equals(region)){
                        t = t.toLowerCase();
                    }
                    //Log.debug("GET REGION: d");
                    Image img = DrawableManager.getResourceImage(region.getDrawable());
                    //Log.debug("GET REGION: e");
                    choices[i] = new ChooserPane.Choice(hum, t, img);
                }
                Text title = new Text(LRes.PLEASE_SELECT_REGION.toString());
                title.setFont(Font.font(20));
                title.setTextAlignment(TextAlignment.CENTER);
                title.setWrappingWidth(WindowManager.getContentWidth()-100);
                Text text = new Text(LRes.PLEASE_SELECT_REGION_TEXT.toString());
                text.setFont(Font.font(16));
                text.setTextAlignment(TextAlignment.CENTER);
                text.setWrappingWidth(WindowManager.getContentWidth()-100);
                //Log.debug("GET REGION: 4");
                ChooserPane chooserPane = new ChooserPane(choices);
                VBox vBox = new VBox(20, title, text, chooserPane);
                vBox.setAlignment(Pos.CENTER);
                WindowManager.setMainContent(vBox, false);
                //Log.debug("GET REGION: 5");
                int choice = chooserPane.getIdClickReceiver().waitClick();
                WindowManager.removeTopContent();
                region = regions[choice];
                Log.info("Selected region: "+region.toString());
                SettingsUtils.setRegion(region);
                return 0;
            }
        };
    }





    public static RunnableMessage FIRST_DISCLAIMER() { return  () -> {
        ButtonPane buttonPane = new ButtonPane(LRes.DONT_AGREE.toString(),LRes.AGREE.toString());
        Text t2 = new Text(RawManager.getDisclaimer());
        t2.setFont(Font.font(15));
        Pane p = new TextScrollPane( t2, LRes.DISCLAIMER.toString());
        p.setPadding(new Insets(20,100,20,100));
        buttonPane.setContent(p);
        WindowManager.setMainContent(buttonPane);
        Log.debug(Thread.currentThread());
        return buttonPane.waitClick();
    };}
    public static RunnableMessage MOD_RECOVER_CHOICE() { return  () -> {
        IDClickReceiver idClickReceiver = new IDClickReceiver();
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                double height = WindowManager.getMainPane().getHeight()*2/3;
                DeviceView running = new DeviceView(DeviceView.DEVICE_18_9,height, Color.WHITE,null), recover = new DeviceView(DeviceView.DEVICE_18_9,height, Color.BLACK, null);
                running.getImagePane().setCursor(Cursor.HAND);
                recover.getImagePane().setCursor(Cursor.HAND);
                recover.setContent(DrawableManager.getPng(DrawableManager.FASTBOOT_LOGO),true);
                running.setContent(DrawableManager.getPng(DrawableManager.MIUI10));
                Button b1 = new CustomButton(LRes.CHOOSE);
                Button b2 = new CustomButton(LRes.CHOOSE);
                b1.setMinWidth(100);
                b1.setFont(Font.font(14));
                b2.setMinWidth(100);
                b2.setFont(Font.font(14));
                Text t2 = new Text(LRes.CHOOSE_RECOVER_DEVICE.toString());
                Text t1 = new Text(LRes.CHOOSE_MOD_DEVICE.toString());
                t1.setFont(Font.font(14));
                t2.setFont(Font.font(14));
                t1.setTextAlignment(TextAlignment.CENTER);
                t2.setTextAlignment(TextAlignment.CENTER);
                VBox vBox1 = new VBox(GuiUtils.center(running), t1, b1);
                VBox vBox2 = new VBox(GuiUtils.center(recover), t2, b2);
                idClickReceiver.addNodes(b1,b2,running.getImagePane(),recover.getImagePane());
                vBox1.setAlignment(Pos.CENTER);
                vBox2.setAlignment(Pos.CENTER);
                HBox hBox = new HBox(vBox1, vBox2);
                hBox.setAlignment(Pos.CENTER);

                hBox.setSpacing(100);
                vBox1.setSpacing(20);
                vBox2.setSpacing(20);
                WindowManager.setMainContent(hBox);
            }
        });
        return idClickReceiver.waitClick()%2;
    };}

    public static RunnableMessage ASK_FOR_FEEDBACK(){
        return new RunnableMessage() {
            @Override
            public int run() throws InterruptedException {
                ButtonPane buttonPane = new ButtonPane(LRes.YES, LRes.NO);
                buttonPane.setContentText(LRes.FEEDBACK_ASK_TO_LEAVE);
                WindowManager.setMainContent(buttonPane, false);
                int click = buttonPane.waitClick();
                if (click != 0){
                    WindowManager.removeTopContent();
                    return 0;
                }
                PopupWindow window = SettingsController.getFeedbackPopupWindow();
                WindowManager.launchPopup(window);
                window.waitForClose();
                WindowManager.removeTopContent();
                return 1;
            }
        };
    }


}
