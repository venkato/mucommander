package com.mucommander.ui.terminal

import com.google.common.collect.Maps
import com.mucommander.conf.MuConfigurations
import com.mucommander.conf.MuPreference
import com.mucommander.conf.MuPreferences
import com.mucommander.conf.MuPreferencesAPI
import com.mucommander.desktop.DesktopManager
import com.pty4j.PtyProcess
import com.pty4j.unix.Pty
import com.pty4j.unix.UnixPtyProcess
import com.pty4j.util.PtyUtil
import com.pty4j.windows.WinPtyProcess
import com.sun.jna.Platform;


class MuTerminalTtyCreator {

    public static MuTerminalTtyCreator creator = new MuTerminalTtyCreator();


    PtyProcess createTerminal(String directory){
        Map<String, String> envs = Maps.newHashMap(System.getenv());
        envs.put("TERM", "xterm-256color");

        MuPreferencesAPI pref = MuConfigurations.getPreferences();
        String cmd;
        if (pref.getVariable(MuPreference.TERMINAL_USE_CUSTOM_SHELL, MuPreferences.DEFAULT_TERMINAL_USE_CUSTOM_SHELL)) {
            cmd = pref.getVariable(MuPreference.TERMINAL_SHELL);
        } else {
            cmd = DesktopManager.getDefaultTerminalShellCommand();
        }

        cmd = cmd.replaceAll("\t", " ").replaceAll(" +", " ");
        String[] command = cmd.split(" ");
        String[] env3 = PtyUtil.toStringArray(envs)
        if (Platform.isWindows()) {
            return new WinPtyProcess(command, env3, directory,true);
        }
        Pty pty = new Pty(false)
        return new UnixPtyProcess(command, env3, directory, pty,pty);
//        return PtyProcess.exec(command, envs, null);
    }


}