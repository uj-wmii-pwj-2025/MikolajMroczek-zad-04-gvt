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
            // jeśli plik już jest śledzony w ostatniej wersji → nic nie zmieniamy
            if (versionMetaData.isFileExist(fileName)) {
                exitHandler.exit(0, "File already added. File: " + path);
            }

            // 1. utwórz nową wersję na bazie ostatniej
            createNewVersionFromLast();

            // 2. dodaj fizycznie nowy plik do katalogu nowej wersji
            Path targetPath = versionDir.resolve(fileName);
            Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 3. zaktualizuj metadane wersji (lista plików + message)
            versionMetaData.addNewFile(fileName);

            String commitMessage;
            if (message == null || message.isEmpty()) {
                // domyślny commit message, gdy użytkownik nic nie podał
                commitMessage = "Added file: " + fileName;
            } else {
                // jeśli jest custom message, na razie bierzemy ją wprost
                commitMessage = message + ".";
            }

            versionMetaData.addNewMessage(commitMessage);
            saveVersionMetaData();

            // 4. komunikat na stdout
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

    // jeśli plik NIE jest śledzony → tylko komunikat, bez tworzenia nowej wersji
    if (!versionMetaData.isFileExist(fileName)) {
        exitHandler.exit(0, "File " + fileName + " is not added to gvt.");
    }

    try {
        // 1. utwórz NOWĄ wersję na bazie ostatniej
        createNewVersionFromLast();

        // 2. w nowej wersji odłącz plik
        if (versionMetaData.isFileExist(fileName)) {
            versionMetaData.detach(fileName);
            try {
                Files.deleteIfExists(versionDir.resolve(fileName));
            } catch (Exception ignore) {
                // brak pliku w wersji – trudno, nie jest krytyczne
            }
        }

        // 3. ustaw message wersji:
        //    pierwsza linia: "Detached file: a.txt"
        //    druga linia (opcjonalna): wiadomość z -m w nowej linii
        StringBuilder msg = new StringBuilder("Detached file: " + fileName);
        if (message != null && !message.isEmpty()) {
            msg.append(System.lineSeparator()).append(message);
        }
        versionMetaData.addNewMessage(msg.toString());

        saveVersionMetaData();

        // 4. komunikat na stdout:
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

    // plik musi istnieć fizycznie
    if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
        exitHandler.exit(51, "File not found. File: " + path);
    }

    // plik musi być już dodany do gvt
    if (!versionMetaData.isFileExist(fileName)) {
        exitHandler.exit(0, "File is not added to gvt. File: " + fileName);
    }

    try {
        // 1️⃣ utwórz nową wersję na bazie ostatniej (bez użycia add())
        createNewVersionFromLast();

        // 2️⃣ skopiuj aktualny plik do katalogu nowej wersji
        Path targetPath = versionDir.resolve(fileName);
        Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 3️⃣ ustaw commit message dla tej wersji
        // history ma zwrócić: "X: Committed file: b.txt"
        // (dalsza obsługa -m możesz dopisać później jako drugi wiersz)
        String commitMessage = "Committed file: " + fileName;
        if (message != null && !message.isEmpty()) {
            // np. druga linia z wiadomością użytkownika
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
        // metadane wersji, którą chcemy przywrócić
        VersionMetaData targetMeta = readVersion(v);

        // katalog z plikami tej wersji: .gvt/v
        Path targetVersionDir = gvtDir.resolve(v.toString());

        // dla każdego pliku śledzonego w wersji v
        for (String fileName : targetMeta.getFileNames()) {
            Path src = targetVersionDir.resolve(fileName);   // .gvt/v/fileName
            Path dest = path.resolve(fileName);              // roboczy katalog: ./fileName

            if (Files.exists(src)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            // jeśli jakimś cudem src nie istnieje, po prostu go pomijamy
            // (w sensownym repo nie powinno się zdarzyć)
        }

        // komunikat sukcesu zgodnie ze specyfikacją
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

    // brak parametru albo -1 → aktualnie aktywna / ostatnia wersja
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
            // tu wypisujemy CAŁY commit message – jeśli w JSON jest "\n",
            // po deserializacji będzie prawdziwy znak nowej linii
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
            // brak wersji – nic nie wypisujemy, ale normalne wyjście
            return;
        }

        if (n == 0) {
            n = N;  // 0 = wszystkie wersje
        }

        // chcemy POKAZAĆ ostatnie n wersji, ale w kolejności rosnącej
        int start = Math.max(0, N - n);

        for (int i = start; i < N; i++) {
            VersionMetaData metaData = readVersion(versions.get(i));

            String fullMessage = metaData.getMessage();
            if (fullMessage == null) {
                fullMessage = "";
            }

            // tylko pierwsza linia
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
    // ostatnia znana wersja
    Integer lastVersion = repoMetaData.getLatestVersion();
    Integer newVersion = lastVersion + 1;

    Path lastVersionDir = gvtDir.resolve(lastVersion.toString());
    Path newVersionDir = gvtDir.resolve(newVersion.toString());

    // utwórz katalog dla nowej wersji
    Files.createDirectory(newVersionDir);

    // skopiuj wszystkie śledzone pliki z poprzedniej wersji
    HashSet<String> newFilesSet = new HashSet<>();
    for (String fileName : versionMetaData.getFileNames()) {
        Path src = lastVersionDir.resolve(fileName);
        Path dest = newVersionDir.resolve(fileName);
        if (Files.exists(src)) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        newFilesSet.add(fileName);
    }

    // zaktualizuj repoMetaData – nowa wersja + ustaw ją jako "current"
    repoMetaData.addVersion(newVersion);
    repoMetaData.setVersion(newVersion);
    saveRepoMetaData();

    // meta nowej wersji – UWAGA: message = "" (puste, nic nie dziedziczymy)
    versionDir = newVersionDir;
    versionMetaFile = newVersionDir.resolve("meta.json");

    versionMetaData = new VersionMetaData(
        newVersion,
        "",          // ← tu jest klucz – każda nowa wersja startuje z pustym message
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
                // dodac obsluge
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
