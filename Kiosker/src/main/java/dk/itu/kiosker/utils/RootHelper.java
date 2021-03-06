package dk.itu.kiosker.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class RootHelper {
    public static Boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    public static Boolean checkRootMethod2() {
        try {
            File file = new File("/system/app/Superuser.apk");
            return file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static Boolean checkRootMethod3() {
        return new RootHelper().executeCommand() != null;
    }

    public ArrayList<String> executeCommand() {
        String line;
        ArrayList<String> fullResponse = new ArrayList<>();
        Process localProcess;
        try {
            localProcess = Runtime.getRuntime().exec(SHELL_CMD.check_su_binary.command);
        } catch (Exception e) {
            return null;
        }
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                localProcess.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(
                localProcess.getInputStream()));
        try {
            while ((line = in.readLine()) != null) {
                fullResponse.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fullResponse;
    }

    public static enum SHELL_CMD {
        check_su_binary(new String[]{"/system/xbin/which", "su"});

        final String[] command;

        SHELL_CMD(String[] command) {
            this.command = command;
        }
    }
}
