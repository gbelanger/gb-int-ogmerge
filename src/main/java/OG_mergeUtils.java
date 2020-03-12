import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;


public final class OG_mergeUtils {

    //  Formating variables
    public static DecimalFormat twoDigits = new DecimalFormat("0.00");
    public static DecimalFormat threeDigits = new DecimalFormat("0.000");
    public static DecimalFormat timeFormat = new DecimalFormat("0.000E0");
    public static String sep = File.separator;

    public static InputStream getFileFromJarAsStream(String name) {
	return ClassLoader.getSystemResourceAsStream(name);
    }

    public static void createOutputDirectory(String dirName) throws IOException {
	//  Check if output directory exists and ask to overwrite
	MyFile newOGdir = new MyFile(dirName);
	String newOG = newOGdir.getPath();
	File parent = newOGdir.getParentFile();
	if ( parent.canWrite() ) {
	    if ( newOGdir.exists() ) {
		if ( newOGdir.canWrite() ) {
		    OG_merge.logger.warn("Directory "+newOG +" exists.");
		    System.out.print("Overwrite? ");
		    BufferedReader is = new BufferedReader( new InputStreamReader(System.in) );
		    String run = is.readLine();
		    while ( ! run.equals("y") && ! run.equals("n") ) {
			OG_merge.logger.warn("Please type 'y' or 'n' ");
			run = is.readLine();
		    }
		    if ( run.equals("y") ) {
			OG_merge.logger.info("Deleting directory "+newOG);
			newOGdir.rm();
		    }
		    else { System.exit(-1); }
		    is.close();
		}
		else {
		    OG_merge.logger.fatal("Cannot write: "+newOGdir.getCanonicalPath());
		    System.exit(-1);
		}
	    }
	    else {
		newOGdir.mkdirs();
		if ( newOGdir.exists() ) {
		    OG_merge.logger.info("Created output directory: "+newOG);
		}
		else {
		    OG_merge.logger.fatal("Could not create dir: "+newOG);
		    System.exit(-1);
		}
	    }
	}
	else {
	    OG_merge.logger.fatal("Cannot write to parent dir: "+parent.getCanonicalPath());
	    System.exit(-1);
	}
    }

    public static ArrayList[] getImageFileArrayListFromScwNums(String[] scwNums, String[] dataDirNames) throws IOException {
	OG_merge.logger.info("Selecting ISGRI images according to scw list ...");
	ArrayList imageFilesArrayList = new ArrayList();
	ArrayList swgFilesArrayList = new ArrayList();
	ArrayList selectedDirsArrayList = new ArrayList();
	ArrayList selectedScwNumsArrayList = new ArrayList();
	String imaFilenameGZ = "isgri_sky_ima.fits.gz";
	String imaFilename = "isgri_sky_ima.fits";
	String swgIdxFilenameGZ = "swg_idx_ibis.fits.gz";
	String swgIdxFilename = "swg_idx_ibis.fits";
	int k=0;
	int i=0;
	while ( i < dataDirNames.length && k < scwNums.length ) {
	    String dirname = dataDirNames[i];
	    String scwNum = scwNums[k];
	    String rev = scwNum.substring(0,4);
	    //OG_merge.logger.info("dirname="+dirname+" scwNum="+scwNum+" rev="+rev);
	    while ( dirname.contains(sep + rev + sep) ) {
		String fullDirname = dirname + sep + scwNum + sep + "scw" + sep + scwNum+".001";
		String fullFilenameGZ = fullDirname + sep + imaFilenameGZ;
		String fullFilename = fullDirname + sep + imaFilename;
		File imaGZ = new File(fullFilenameGZ);
		File ima = new File(fullFilename);
		MyFile dir = new MyFile(fullDirname);
		boolean imaGZExists = imaGZ.exists();
		boolean imaExists = ima.exists();
		if ( imaGZExists || imaExists ) {
		    //OG_merge.logger.info("  Image exists");
		    // If image exists, check if there is an swg index file for it
		    String swgIdxFullFilenameGZ =  dirname + sep + scwNum + sep + swgIdxFilenameGZ;
		    String swgIdxFullFilename =  dirname + sep + scwNum + sep + swgIdxFilename;
		    MyFile swgGZ = new MyFile(swgIdxFullFilenameGZ);
		    MyFile swg = new MyFile(swgIdxFullFilename);
		    if ( swgGZ.exists() || swg.exists() ) {
			//OG_merge.logger.info("  Swg_idx exists");
			// If there is an image and an swg index then add the image, swg, dir and scwID to lists
			selectedDirsArrayList.add(dir);
			selectedScwNumsArrayList.add(scwNum);
			if ( imaGZExists ) imageFilesArrayList.add(imaGZ);
			else imageFilesArrayList.add(ima);
			if ( swgGZ.exists() ) swgFilesArrayList.add(swgGZ);
			else swgFilesArrayList.add(swg);
		    }
		}
		k++;
		try {
		    scwNum = scwNums[k];
		    rev = scwNum.substring(0,4);
		}
		catch ( ArrayIndexOutOfBoundsException e ) {
		    break;
		}
	    }
	    i++;
	}
	imageFilesArrayList.trimToSize();
	swgFilesArrayList.trimToSize();
	selectedDirsArrayList.trimToSize();
	selectedScwNumsArrayList.trimToSize();
	OG_merge.logger.info("  "+imageFilesArrayList.size()+" images selected (of "+scwNums.length+" scwIDs)");
	return new ArrayList[]{imageFilesArrayList, swgFilesArrayList, selectedDirsArrayList, selectedScwNumsArrayList};
    }

    public static void constructNewOGStructure(String newOG_path, ArrayList selectedDirs) throws IOException, FileNotFoundException {
	OG_merge.logger.info("Constructing new OG structure");
	OG_merge.logger.info("Creating links to scw directories ("+selectedDirs.size()+")");
	int count = 1;
	String linkParentPath = newOG_path + sep +"scw";
	MyFile linkParent = new MyFile(linkParentPath);
	linkParent.mkdirs();
	//  Create and write script to be executed to create links
	String tempFilename = "createLinks"+Math.random()+".sh";
	PrintWriter bw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tempFilename)));
	for ( int j=0; j < selectedDirs.size(); j++ ) {
	    String targetPath = ( ((MyFile) selectedDirs.get(j)).getCanonicalPath() );
	    bw.println("/bin/ln -s "+targetPath+" "+linkParentPath);
	}
	bw.close();
	// Execute the script that will create the links and then delete it
	MyFile tempFile = new MyFile(tempFilename);
	tempFile.chmod(755);
	tempFile.deleteOnExit();
	boolean linksCreated = systemCall(new String[]{"./"+tempFile.getName()});
	if ( linksCreated ) {
	    OG_merge.logger.info("  Task createLinks complete");
	    tempFile.delete();
	}
	else {
	    OG_merge.logger.fatal("  Could not create links to data. Don't know why.");
	    System.exit(-1);
	}
	// Create mosaicked results directory
	String newOG_obs_myobs = newOG_path;
	MyFile mosaDir = new MyFile(newOG_obs_myobs);
	mosaDir.mkdirs();
    }

    public static boolean systemCall(String[] _args) {
	Runtime rt = Runtime.getRuntime();
	try {
	    Process p = rt.exec(_args);
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

    private static ArrayList recursivelyListFiles(String[] dataDirNames) {
	OG_merge.logger.info("Scanning input data ...");
	ArrayList myFileArrayList = new ArrayList();
	for ( int j=0; j < dataDirNames.length; j++ ) { 
	    //OG_merge.logger.info(""+(j+1)+") " + dataDirNames[j] + " ... ");
	    try {
		MyFile.fileList(myFileArrayList, dataDirNames[j]);
	    }
	    catch ( NullPointerException e ) {
		OG_merge.logger.warn("No such directory ("+dataDirNames[j]+")");
	    }
	}
	return myFileArrayList;
    }

    public static double getSum(double[] data) {
	double sum = 0;
	for ( int i=0; i < data.length; i++ ) {
	    sum += data[i];
	}
	return sum;
    }

}