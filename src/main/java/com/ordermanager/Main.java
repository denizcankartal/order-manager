package com.ordermanager;

import com.ordermanager.cli.OrderManagerCLI;
import picocli.CommandLine;

/**
 * Main entry point for the Order Manager CLI
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OrderManagerCLI()).execute(args);
        System.exit(exitCode);
    }
}
