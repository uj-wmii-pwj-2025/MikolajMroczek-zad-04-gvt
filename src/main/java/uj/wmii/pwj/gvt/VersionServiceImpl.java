package uj.wmii.pwj.gvt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uj.wmii.pwj.gvt.ExitHandler;
import uj.wmii.pwj.gvt.RepoMetaData;
import uj.wmii.pwj.gvt.VersionMetaData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.Reader;

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

        if(isInitialized())
        {
            try
            {
                loadMetaData();
            }
            catch(Exception e)
            {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details");  
            }
        }
    }

    @Override
    public void init(String message)
    {   
        if(Files.exists(gvtDir) && Files.isDirectory(gvtDir))
        {
            exitHandler.exit(10, "Current directory is already initialized.");
        }  

        try
        {
            setupDirectory(message);            
        }
        catch(Exception e)
        {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details");  
        }
    };

    @Override
    public void add(String path, String message)
    {   
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        Path filePath = Paths.get(path);
        if(!Files.exists(filePath) || Files.exists(filePath) && Files.isDirectory(filePath))
        {
            exitHandler.exit(21, "File not found. File: " + path.toString());
        }

        try
        {
            if(versionMetaData.isFileExist(filePath.getFileName().toString()))
            {
                exitHandler.exit(0, "File already added. File: " + path);
            }

            Path targetPath = versionDir.resolve(filePath.getFileName().toString());

            Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            versionMetaData.addNewFile(filePath.getFileName().toString());
            versionMetaData.addNewMessage(message + ".");

            saveVersionMetaData();
        }
        catch(Exception ex)
        {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    };


    @Override
    public void detach(String path, String message)
    {   
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        Path filePath = Paths.get(path);
        String fileName = filePath.getFileName().toString();

        if(versionMetaData.isFileExist(fileName))
        {
            versionMetaData.detach(fileName);
            try
            {
                saveVersionMetaData();
            }
            catch(Exception e)
            {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
            }

            exitHandler.exit(0, "File detached successfully. File:" + fileName);
        }
        else
        {
            exitHandler.exit(0, "File is not added to gvt. File:" + fileName);
        }
    };
    
    @Override
    public void commit(String path, String message)
    {   
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        try
        {   
            Integer newVersion = repoMetaData.getLatestVersion() + 1;            

            repoMetaData.addVersion(newVersion);
            repoMetaData.setVersion(newVersion);

            createVersionDir(newVersion, message + ".");

            saveRepoMetaData();
            loadVersionMetaData();

            add(path, "");
        }   
        catch(Exception ee)
        {
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    };
    
    @Override
    public void checkout(Integer v)
    {
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        if(repoMetaData.isVersionExisting(v))
        {   
            try
            {
                VersionMetaData metaData = readVersion(v);            
            
                HashSet<String> currVersionFileNames = new HashSet<String>(versionMetaData.getFileNames());

                ArrayList<String> toChange = new ArrayList<>();

                for(String version : metaData.getFileNames())
                {
                    if(currVersionFileNames.contains(version)) toChange.add(version);
                }

                Path versionPath = gvtDir.resolve(v.toString());
                Path currVersionPath = gvtDir.resolve(versionMetaData.getVersion().toString());

                for(String fileName : toChange)
                {
                    Path srcPath = versionPath.resolve(fileName);    
                    Path destPath = currVersionPath.resolve(fileName);

                    Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
                }

            }
            catch(Exception e)
            {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
            }
        }   
        else
        {   
            exitHandler.exit(60, "Invalid version number: " + v);
        }   
    };      

    @Override
    public void version(Integer version)
    {
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        if(version == -1)
        {
            version = repoMetaData.getLatestVersion();
        }

        if(repoMetaData.isVersionExisting(version))
        {
            try
            {
                VersionMetaData metaData = readVersion(version);
                System.out.println("Version: " + metaData.getVersion());
                System.out.println(metaData.getMessage());
            }
            catch(Exception ee)
            {
                exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
            }   
        }
        else
        {
            exitHandler.exit(60, "Invalid version number:" + version);
        }

    };

    @Override
    public void history(Integer n)
    {
        if(!isInitialized())
        {
            exitHandler.exit(-2, """
            Current directory is not initialized. Please use "init" command to initialize.
            """);
        }

        ArrayList<Integer> versions = repoMetaData.getVersions();
        ArrayList<String> result = new ArrayList<>();
        try
        {   
            int N = versions.size();
            if (n == 0) n = N;

            for(int i = N - 1; i >= 0 && (N - i) <= n; i--)
            {   
                VersionMetaData metaData = readVersion(versions.get(i));

                result.add(metaData.getVersion() + ": " + metaData.getMessage());
            }        
        }
        catch(Exception e)
        {   
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }

        for(String mess : result)
        {
            System.out.println(mess);
        }
    };


    private boolean isInitialized()
    {
        if(!Files.exists(gvtDir) || !Files.isDirectory(gvtDir) || !Files.exists(repoMetaFile) || Files.isDirectory(repoMetaFile))
        {
            return false;
        }

        return true;
    }

    private VersionMetaData readVersion(Integer v) throws Exception
    {
        versionDir = gvtDir.resolve(v.toString());
        versionMetaFile = versionDir.resolve("meta.json");

        Reader reader = Files.newBufferedReader(versionMetaFile);

        VersionMetaData metaData = gson.fromJson(reader, VersionMetaData.class);
        
        return metaData;
    }


    private void setupDirectory(String message) throws Exception
    {
        Files.createDirectory(gvtDir);

        this.repoMetaData = new RepoMetaData(0);

        saveRepoMetaData();

        createVersionDir(0, message);
    }

    private void createVersionDir(Integer version, String message) throws Exception
    {   
        Path versionDirPath = gvtDir.resolve(version.toString());

        if(Files.exists(versionDirPath))
        {
            Path versionMetaFilePath = versionDirPath.resolve("meta.json");
            if(Files.exists(versionMetaFilePath) && !Files.isDirectory(versionMetaFilePath))
            {
                // dodac obsluge
                return; 
            }
        }

        Files.createDirectory(versionDirPath);
        
        versionMetaData = new VersionMetaData(version, message , new HashSet<String>());
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
        try(Reader reader = Files.newBufferedReader(repoMetaFile))
        {
            repoMetaData = gson.fromJson(reader, RepoMetaData.class);            
        }
    }

    private void loadVersionMetaData() throws Exception
    {
        if(repoMetaData.getCurrentVersion() == null) return;

        versionDir = gvtDir.resolve(repoMetaData.getCurrentVersion().toString());
        versionMetaFile = versionDir.resolve("meta.json");

        try(Reader reader = Files.newBufferedReader(versionMetaFile))
        {
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
