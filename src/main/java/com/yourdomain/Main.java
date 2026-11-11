package com.yourdomain;

import com.yourdomain.dao.DatabaseInitializer;
import com.yourdomain.ui.Authentication;

public class Main {
    public static void main(String[] args) {
        DatabaseInitializer.initialize();   // creates tables (incl. activity_logs) if missing
        Authentication.start();             // login / register loop
    }
}
