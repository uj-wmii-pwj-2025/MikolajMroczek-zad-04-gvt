package uj.wmii.pwj.gvt;

public interface VersionService 
{    
    void init(String message);
    void add(String path, String message);
    void detach(String path, String message);
    void commit(String path, String message);
    void checkout(Integer version);
    void version(Integer version);
    void history(Integer n);
}
