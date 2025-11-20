package uj.wmii.pwj.gvt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;

public class VersionServiceImpl implements VersionService
{
    private final ExitHandler exitHandler;

    private final Path path;
    private final Path gvtDir;
    private final Path repoMetaFile;

    private Path versionDir;
    private Path versionMetaFile;

    private final Gson gson;

    private RepoMetaData repoMetaData;
    private VersionMetaData versionMetaData;

    public VersionServiceImpl(String path, ExitHandler exitHandler)
    {
        this.path = Paths.get(path);
        this.gvtDir = this.path.resolve(".gvt");
        this.repoMetaFile = gvtDir.resolve("repo.json");

        this.exitHandler = exitHandler;
        gson = new GsonBuilder().setPrettyPrinting().create();

        if (isInitialized()) {
            try {
                loadMetaData();
            } catch (Exception e) {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details");
            }
        }
    }

    @Override
    public void init(String message)
    {
        if (Files.exists(gvtDir) && Files.isDirectory(gvtDir)) {
            exitHandler.exit(10, "Current directory is already initialized.");
        }

        try {
            setupDirectory(message);
        } catch (Exception e) {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details");
        }

        exitHandler.exit(0, "Current directory initialized successfully.");
    }

    @Override
    public void add(String path, String message)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath) || (Files.exists(filePath) && Files.isDirectory(filePath))) {
            exitHandler.exit(21, "File not found. File: " + path);
        }

        String fileName = filePath.getFileName().toString();

        try {
            if (versionMetaData.isFileExist(fileName)) {
                exitHandler.exit(0, "File already added. File: " + path);
            }

            createNewVersionFromLast();

            Path targetPath = versionDir.resolve(fileName);
            Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            versionMetaData.addNewFile(fileName);

            String commitMessage;
            if (message == null || message.isEmpty()) {
                commitMessage = "Added file: " + fileName;
            } else {
                commitMessage = message + ".";
            }

            versionMetaData.addNewMessage(commitMessage);
            saveVersionMetaData();

            System.out.println("File " + path + " added successfully.");

        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    @Override
    public void detach(String path, String message)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        Path filePath = Paths.get(path);
        String fileName = filePath.getFileName().toString();

        if (!versionMetaData.isFileExist(fileName)) {
            exitHandler.exit(0, "File " + fileName + " is not added to gvt.");
        }

        try {
            createNewVersionFromLast();

            if (versionMetaData.isFileExist(fileName)) {
                versionMetaData.detach(fileName);
                try {
                    Files.deleteIfExists(versionDir.resolve(fileName));
                } catch (Exception ignore) {
                }
            }

            StringBuilder msg = new StringBuilder("Detached file: " + fileName);
            if (message != null && !message.isEmpty()) {
                msg.append(System.lineSeparator()).append(message);
            }
            versionMetaData.addNewMessage(msg.toString());

            saveVersionMetaData();

            exitHandler.exit(0, "File " + fileName + " detached successfully.");

        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    @Override
    public void commit(String path, String message)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        Path filePath = Paths.get(path);
        String fileName = filePath.getFileName().toString();

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            exitHandler.exit(51, "File not found. File: " + path);
        }

        if (!versionMetaData.isFileExist(fileName)) {
            exitHandler.exit(0, "File is not added to gvt. File: " + fileName);
        }

        try {
            createNewVersionFromLast();

            Path targetPath = versionDir.resolve(fileName);
            Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            String commitMessage = "Committed file: " + fileName;
            if (message != null && !message.isEmpty()) {
                commitMessage = commitMessage + System.lineSeparator() + message;
            }

            versionMetaData.addNewMessage(commitMessage);
            saveVersionMetaData();

            System.out.println("File " + path + " committed successfully.");

        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(52, "File cannot be committed, see ERR for details. File: " + fileName);
        }
    }

    @Override
    public void checkout(Integer v)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        if (!repoMetaData.isVersionExisting(v)) {
            exitHandler.exit(40, "Invalid version number: " + v);
        }

        try {
            VersionMetaData targetMeta = readVersion(v);

            Path targetVersionDir = gvtDir.resolve(v.toString());

            for (String fileName : targetMeta.getFileNames()) {
                Path src = targetVersionDir.resolve(fileName);
                Path dest = path.resolve(fileName);

                if (Files.exists(src)) {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            System.out.println("Checkout successful for version: " + v);

        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    @Override
    public void version(Integer version)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        if (version == null || version == -1) {
            Integer curr = repoMetaData.getCurrentVersion();
            if (curr != null) {
                version = curr;
            } else {
                version = repoMetaData.getLatestVersion();
            }
        }

        if (repoMetaData.isVersionExisting(version)) {
            try {
                VersionMetaData metaData = readVersion(version);

                String msg = metaData.getMessage();
                if (msg == null) {
                    msg = "";
                }

                System.out.println("Version: " + metaData.getVersion());
                System.out.print(msg);

            } catch (Exception ee) {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
            }
        } else {
            exitHandler.exit(60, "Invalid version number: " + version + ".");
        }
    }

    @Override
    public void history(Integer n)
    {
        if (!isInitialized()) {
            exitHandler.exit(
                -2,
                "Current directory is not initialized. Please use \"init\" command to initialize."
            );
        }

        ArrayList<Integer> versions = repoMetaData.getVersions();
        ArrayList<String> result = new ArrayList<>();

        try {
            int N = versions.size();
            if (N == 0) {
                return;
            }

            if (n == 0) {
                n = N;
            }

            int start = Math.max(0, N - n);

            for (int i = start; i < N; i++) {
                VersionMetaData metaData = readVersion(versions.get(i));

                String fullMessage = metaData.getMessage();
                if (fullMessage == null) {
                    fullMessage = "";
                }

                int idx = fullMessage.indexOf('\n');
                String firstLine = (idx >= 0) ? fullMessage.substring(0, idx) : fullMessage;

                result.add(metaData.getVersion() + ": " + firstLine);
            }
        } catch (Exception e) {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }

        for (String mess : result) {
            System.out.println(mess);
        }
    }

    private boolean isInitialized()
    {
        if (!Files.exists(gvtDir)
            || !Files.isDirectory(gvtDir)
            || !Files.exists(repoMetaFile)
            || Files.isDirectory(repoMetaFile)) {
            return false;
        }
        return true;
    }

    private VersionMetaData readVersion(Integer v) throws Exception
    {
        versionDir = gvtDir.resolve(v.toString());
        versionMetaFile = versionDir.resolve("meta.json");

        try (Reader reader = Files.newBufferedReader(versionMetaFile)) {
            return gson.fromJson(reader, VersionMetaData.class);
        }
    }

    private void setupDirectory(String message) throws Exception
    {
        Files.createDirectory(gvtDir);

        this.repoMetaData = new RepoMetaData(0);

        saveRepoMetaData();

        createVersionDir(0, message);
    }

    private void createNewVersionFromLast() throws Exception
    {
        Integer lastVersion = repoMetaData.getLatestVersion();
        Integer newVersion = lastVersion + 1;

        Path lastVersionDir = gvtDir.resolve(lastVersion.toString());
        Path newVersionDir = gvtDir.resolve(newVersion.toString());

        Files.createDirectory(newVersionDir);

        HashSet<String> newFilesSet = new HashSet<>();
        for (String fileName : versionMetaData.getFileNames()) {
            Path src = lastVersionDir.resolve(fileName);
            Path dest = newVersionDir.resolve(fileName);
            if (Files.exists(src)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            newFilesSet.add(fileName);
        }

        repoMetaData.addVersion(newVersion);
        repoMetaData.setVersion(newVersion);
        saveRepoMetaData();

        versionDir = newVersionDir;
        versionMetaFile = newVersionDir.resolve("meta.json");

        versionMetaData = new VersionMetaData(
            newVersion,
            "",
            newFilesSet
        );

        saveVersionMetaData();
    }

    private void createVersionDir(Integer version, String message) throws Exception
    {
        Path versionDirPath = gvtDir.resolve(version.toString());

        if (Files.exists(versionDirPath)) {
            Path versionMetaFilePath = versionDirPath.resolve("meta.json");
            if (Files.exists(versionMetaFilePath) && !Files.isDirectory(versionMetaFilePath)) {
                return;
            }
        }

        Files.createDirectory(versionDirPath);

        versionMetaData = new VersionMetaData(version, message, new HashSet<String>());
        versionMetaFile = versionDirPath.resolve("meta.json");

        saveVersionMetaData();
    }

    private void loadMetaData() throws Exception
    {
        loadRepoMetaData();
        loadVersionMetaData();
    }

    private void loadRepoMetaData() throws Exception
    {
        try (Reader reader = Files.newBufferedReader(repoMetaFile)) {
            repoMetaData = gson.fromJson(reader, RepoMetaData.class);
        }
    }

    private void loadVersionMetaData() throws Exception
    {
        if (repoMetaData.getCurrentVersion() == null) return;

        versionDir = gvtDir.resolve(repoMetaData.getCurrentVersion().toString());
        versionMetaFile = versionDir.resolve("meta.json");

        try (Reader reader = Files.newBufferedReader(versionMetaFile)) {
            versionMetaData = gson.fromJson(reader, VersionMetaData.class);
        }
    }

    private void saveRepoMetaData() throws Exception
    {
        String json = gson.toJson(repoMetaData);
        Files.writeString(repoMetaFile, json);
    }

    private void saveVersionMetaData() throws Exception
    {
        String json = gson.toJson(versionMetaData);
        Files.writeString(versionMetaFile, json);
    }
}
