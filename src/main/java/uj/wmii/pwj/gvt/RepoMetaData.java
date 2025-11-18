package uj.wmii.pwj.gvt;

import java.util.ArrayList;
import java.util.HashSet;

public class RepoMetaData 
{
    private Integer latestVersion;
    private Integer currentVersion;
    private HashSet<Integer> versions;    

    public RepoMetaData() {};

    public RepoMetaData(int version)
    {
        this.latestVersion = version;
        this.currentVersion = version;
        this.versions = new HashSet<Integer>();
        this.versions.add(version);
    }

    public void setVersion(int version)
    {   
        if(versions.contains(version))
        {
            this.currentVersion = version;
        }
    }

    public void addVersion(int version)
    {
        this.latestVersion = version;
        this.versions.add(version);
    }

    public Integer getCurrentVersion()
    {
        return currentVersion;
    }

    public Integer getLatestVersion()
    {
        return latestVersion;
    }

    public ArrayList<Integer> getVersions()
    {
        return new ArrayList<Integer>(versions);
    } 

    public boolean isVersionExisting(int x)
    {
        return versions.contains(x);
    }

}
