import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;


public class MyFile extends File {

    /**
       This class extends File by  number of methods that
       are useful for handling data files
       
       @author Guillaume Belanger (ESAC)
       @version 2 Feb 2007
       
       
      Last modified
     
      September 20, 2012

      - Replaced all Vector by ArrayList
      - Removed all the writeData() methods
      - Removed all the methods related to getting data from a text file
      - Added a sort command to all filled ArrayLists
     
     **/

    private boolean select;
    public int bufferSize = 4096;


    /**  CONSTRUCTORS  **/

    public MyFile(File myfile) throws IOException {
	super(myfile.getCanonicalPath());
	this.offSelect();
    }

    public MyFile(String filename) throws IOException {
	super(filename);
	this.offSelect();
    }


    /**  STATIC METHODS  **/

    public static void fileList(ArrayList myFileList, String path) {
 	
	// Routine that scans and lists all dirs 
	// and files recursively from the input path
	// and fills myFileList with MyFile objects
	try {
	    MyFile myfile = new MyFile(path);
	    MyFile[] files = myfile.listMyFiles();
	    for ( int i=0; i < files.length; i++ ) {
		if ( files[i].isDirectory() ) {
		    if ( !(files[i].getCanonicalPath()).equals(path) ) 
			fileList(myFileList, files[i].getCanonicalPath());
		}
		if ( files[i].isFile() || files[i].isDirectory() ) 
		     myFileList.add(files[i]);
		    //if ( !files[i].isLink() ) myFileList.add(files[i]);
	    }
	    Collections.sort(myFileList);
	}
	catch(IOException e) {System.out.println(e);}
	return;
    }

    public static ArrayList selectFiles(ArrayList myFileList, String filenameToSelect) {

	// Routine that selects files given 2 args:
	//// 1) ArrayList of MyFile objects,
	//// 2) String argument for the filename of files to search for

	ArrayList selectedFilesList = new ArrayList();
	for (int i=0; i < myFileList.size(); i++) {
	    String filename = ((MyFile) myFileList.get(i)).getName();
	    if ( filename.equals(filenameToSelect) || filename.equals(filenameToSelect+".gz") )
		selectedFilesList.add(myFileList.get(i));
	}
	Collections.sort(selectedFilesList);
	return selectedFilesList;
    }

    public MyFile[] listMyFiles() throws IOException {
	File[] files = super.listFiles();
	MyFile[] myfiles = new MyFile[files.length];
	for (int i=0;i!=files.length;i++) {
	    myfiles[i] = new MyFile(files[i]);
	}
        return myfiles;
    }


    /**   Two static methods taken from FileUtils of qizxopen **/

    /**
     * Copy a file.
     * 
     * @param srcFileName the name of the source file
     * @param dstFileName the name of the destination file
     * @exception IOException if there is an IO problem
     */
    public static void copyFile(String srcFileName, String dstFileName) 
    throws IOException {
        copyFile(new File(srcFileName), new File(dstFileName));
    }
    
    /**  
     * Copy a file. 
     * 
     * @param srcFile source file
     * @param dstFile destination file
     * @exception IOException if there is an IO problem
     */
    public static void copyFile(File srcFile, File dstFile) throws IOException {
	
        FileOutputStream dst = new FileOutputStream(dstFile);
        
        try {
            doCopyFile(srcFile, dst);
        } finally {
            dst.close();
        }
    }
    
    private static void doCopyFile(File srcFile, OutputStream dst) throws IOException {
    
        FileInputStream src = new FileInputStream(srcFile);
        byte[] bytes = new byte[8192];
        int count;
        
        try {
            while ((count = src.read(bytes)) != -1) 
                dst.write(bytes, 0, count);
            
            dst.flush();
        } finally {
            src.close();
        }
    }

    /**
     * Tests if a file has been compressed using gzip.
     * 
     * @param fileName the name of the file to be tested
     * @return <code>true</code> if the file has been gzip-ed,
     * <code>false</code> otherwise
     * @exception IOException if there is an IO problem
     */
    public static boolean isGzipped(String fileName) throws IOException {
        return isGzipped(new File(fileName));
    }
    
    public static boolean isGzipped(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        int magic1 = in.read();
        int magic2 = in.read();
        in.close();
        return (magic1 == 0037 && magic2 == 0213);
    }

    /**  End methods borrowed from FileUtils **/



    /**  NON-STATIC METHODS  **/

    public void onSelect() {
	select = true;
    }

    public void offSelect() {
	select = false;
    }

    public boolean isSelected() {
	return select;
    }

    public boolean rm() {
	try {

	    String[] rm_cmd 
		= new String[]{"rm", "-fr", this.getCanonicalPath()};
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(rm_cmd);
	    int rc = -1;
	    while ( rc == -1 ) {
		try {
		    rc = p.waitFor();
		}
		catch (InterruptedException e) { }
	    }
	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }	    	


    public boolean gunzip() {
	try {
	    //FileUtil fileUtil = new FileUtil();
	    if ( !this.isGzipped(this) ) {
		System.out.println("Error: File "+this.getCanonicalPath()+" is not in gzip format");
		System.exit(-1);
	    }
	    String[] gunzip_cmd = new String[]{"gunzip", this.getCanonicalPath()};
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(gunzip_cmd);
	    int rc = -1;
	    while ( rc == -1 ) {
		try {
		    rc = p.waitFor();
		}
		catch (InterruptedException e) { }
	    }
	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }

    public boolean gzip() {
	try {

	    //FileUtil fileUtil = new FileUtil();
	    if ( this.isGzipped(this) ) {
		System.out.println
		    ("Error: File "+this.getCanonicalPath()+" is already in gzip format");
		System.exit(-1);
	    }

	    String[] gzip_cmd = new String[]{"gzip", this.getCanonicalPath()};
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(gzip_cmd);
	    int rc = -1;
	    while ( rc == -1 ) {
		try {
		    rc = p.waitFor();
		}
		catch (InterruptedException e) { }
	    }
	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }

    public boolean chmod(int mod) {

	try {
	    String[] chmod_cmd 
		= new String[]{"chmod", (new Integer(mod)).toString(), this.getCanonicalPath()};
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(chmod_cmd);
	    int rc = -1;
	    try {  rc = p.waitFor(); }
	    catch (InterruptedException e) { }

	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }	    

    public boolean chmod(String[] cmd) {

	try {
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(cmd);
	    int rc = -1;
	    try {  rc = p.waitFor(); }
	    catch (InterruptedException e) { }

	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }	    

    public boolean source() {

	try {
	    this.chmod(755);
	    Runtime rt = Runtime.getRuntime();
	    Process p = rt.exec(this.getCanonicalPath());
	    int rc = -1;
	    try {  rc = p.waitFor(); }
	    catch (InterruptedException e) { }

	    return rc == 0;
	}
	catch (IOException e) {return false;}
    }

    public boolean isLink() {

	try {
	    if ( !this.exists() )  return true;
	    else {
		String cnnpath = this.getCanonicalPath();
		String abspath = this.getAbsolutePath();
		return !abspath.equals(cnnpath);
	    }
	}
	catch (IOException ex) {
	    System.err.println(ex);
	    return true;
	}
    }


}
