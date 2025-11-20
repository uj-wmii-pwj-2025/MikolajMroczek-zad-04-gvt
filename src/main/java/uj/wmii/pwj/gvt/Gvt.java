package uj.wmii.pwj.gvt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Gvt {

    private final ExitHandler exitHandler;
    private VersionService versionService;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }
    
    public void mainInternal(String... args) {     
        ExitHandler termiantor = new ExitHandler();

        if (args == null || args.length == 0) {
            termiantor.exit(1, "Please specify command.");
            return;
        }

        versionService = new VersionServiceImpl(".", new ExitHandler());

        String command = args[0];
        switch (command) {
            case "init":
                handleInit(args);
                break;
            case "add":
                handleAdd(args);
                break;
            case "detach":
                handleDetach(args);
                break;
            case "commit":
                handleCommit(args);
                break;
            case "checkout":
                handleCheckout(args);
                break;
            case "history":
                handleHistory(args);
                break;
            case "version":
                handleVersion(args);
                break;
            default:
                exitHandler.exit(1, "Unknown command " + command + ".");
                break;
        }
    }

    private String extractUserMessage(String... args) {
        if (args.length >= 3 && "-m".equals(args[args.length - 2])) {
            return args[args.length - 1];
        }
        return "";
    }

    private void handleInit(String... args) {
        versionService.init("GVT initialized.");
    }

    private void handleAdd(String... args) {
        String userMessage = extractUserMessage(args);

        String fileName = null;
        if (args.length >= 2 && !"-m".equals(args[1])) {
            fileName = args[1];
        }

        if (fileName == null) {
            exitHandler.exit(20, "Please specify file to add.");
            return;
        }

        versionService.add(fileName, userMessage);
    }

    private void handleDetach(String... args) {
        String userMessage = extractUserMessage(args);

        String fileName = null;
        if (args.length >= 2 && !"-m".equals(args[1])) {
            fileName = args[1];
        }

        if (fileName == null) {
            exitHandler.exit(30, "Please specify file to detach.");
            return;
        }

        versionService.detach(fileName, userMessage);
    }

    private void handleCommit(String... args) {
        String userMessage = extractUserMessage(args);

        String fileName = null;
        if (args.length >= 2 && !"-m".equals(args[1])) {
            fileName = args[1];
        }

        if (fileName == null) {
            exitHandler.exit(50, "Please specify file to commit.");
            return;
        }

        versionService.commit(fileName, userMessage);
    }

    private void handleCheckout(String... args) {
        if (args.length < 2) {
            exitHandler.exit(60, "Invalid version number: ");
            return;
        }

        String versionStr = args[1];
        try {
            Integer v = Integer.valueOf(versionStr);
            versionService.checkout(v);
        } catch (NumberFormatException e) {
            exitHandler.exit(60, "Invalid version number: " + versionStr);
        }
    }

    private void handleHistory(String... args) {
        Integer last = null;

        if (args.length >= 3 && "-last".equals(args[1])) {
            try {
                last = Integer.valueOf(args[2]);
            } catch (NumberFormatException e) {
            }
        }

        if (last == null) {
            versionService.history(0);
        } else {
            versionService.history(last);
        }
    }

    private void handleVersion(String... args) {
        if (args.length < 2) {
            versionService.version(null);
            return;
        }

        String versionStr = args[1];
        try {
            Integer v = Integer.valueOf(versionStr);
            versionService.version(v);
        } catch (NumberFormatException e) {
            exitHandler.exit(60, "Invalid version number: " + versionStr + ".");
        }
    }
}
