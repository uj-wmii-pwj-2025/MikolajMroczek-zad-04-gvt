package uj.wmii.pwj.gvt;

import java.lang.Runtime.Version;
import java.util.HashSet;
import java.util.ArrayList;

public class VersionMetaData 
{   
    private final Integer version;
    private StringBuilder message;
    private HashSet<String> trackedFiles;

    public VersionMetaData() 
    {
        version = 0;
        message = null;
        trackedFiles = null;
    };

    public VersionMetaData(Integer version , String message , HashSet<String> fileNames)
    {
        this.trackedFiles = fileNames;
        this.message = new StringBuilder(message); 
        this.version = version;
    }
    
    public Integer getVersion()
    {
        return version;
    }

    public ArrayList<String> getFileNames()
    {
        return new ArrayList<String>(trackedFiles);
    }

    public boolean isFileExist(String name)
    {
        return trackedFiles.contains(name);
    }

    public void detach(String name)
    {
        trackedFiles.remove(name);
    }

    public void addNewFile(String name)
    {
        trackedFiles.add(name);
    }

    public void addNewMessage(String message)
    {
        this.message.append(message);
    }   

    public String getMessage()
    {
        return this.message.toString();
    }
}