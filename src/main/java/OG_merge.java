
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import nom.tam.fits.*;
import nom.tam.util.BufferedDataInputStream;
import nom.tam.util.BufferedDataOutputStream;
import nom.tam.util.BufferedFile;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import cern.colt.list.IntArrayList;


/**

September 2015

- Important changes inspired by rewrite of SelectSkyIma-2.0
- Modifed the logger configuration to use UUID
- Combined the selected of images with that of swg and directories

September 2014

- Fixed a bug in the image selection algorithm found in method Utils.generateImageFilesArrayListFromScwNums(): the entire method was rewritten and the looping was modified to be more efficient and take advantage of the fact that the dataDirNames are always derived from the list of science windows, AND that both the string arrays scwNums and dataDirNames are sorted. Therefore, the first scw will be from the first rev, and only revs for which there are scw will appear in dataDirNames.

July 2014

- Rewrote relying on the fact that the file structure is known (/isoc5/gbelange/isocArchive/)
- This makes things much simpler, but only works for this specific file structure. 
- Older versions should be used for other, general cases with arbitrary file structure

September 2012

- Replaced fdelrow and fparkey with native nom.tam.fits methods, so that the extra rows in the merged swg_idx file are now deleted without an external call to fdelrow, the same for the rows in og_ibis.fits to which the keyword OGID is also added using nom.tam.fits. Only the sorting remains as an external call because such a method doesn't exit in the fits package.
- Replaced Vector by ArrayList here and in MyFile.java
- Removed calls to ftsort (the last of the Heatools executables used), by sorting the lists at their origin using MyFile methods.

July 2011

- Complete re-structuring of the code
- Dropped HDU checking that was never used anyway
- Fixed a bug in the deleteUnselectedRows:
          There was a line containing "r++" right after the deletion of the first block. This incremented the index incorrectly to the next selected row, leaving the unselected rows between the first and second selected rows without deleting them.
- Fixed a bug in selectScwDir 
          The primary selection was done by looping over the selectedImageFiles. Now is it done by looping on scwNum instead. This resolves the problem of the same directory twice due to the presence in it of both isgri_sky_ima.fits and isgri_sky_ima.fits.gz, even if this shouldn't happen.

June 2010

- Try to open 'rebinned_corr_ima.fits' and 'rebinned_back_ima.fits' and catch Exception.
- Modified keyword OGID in og_ibis.fits to "myobs"

March 2010

- Modified algorithm to delete rows of the merged swg_ibis_idx.fits

**/

public class OG_merge {

    private static Date todaysDate = new Date();
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static String today = sdf.format(todaysDate);
    private static double tstart;
    private static double tstop;
    private static double telapse;

    public static String version = "4.0";
    //  Defined in handleArguments()
    public static String scwLisName = null; 
    public static String newOG = null;
    public static String[] dataDirNames = null;
    public static String newOG_obs_myobs = null;    
    public static String[] scwNums;

    public static void main(String[] args) throws IOException, NullPointerException, FitsException, Exception {
	configureLogger();
	handleArguments(args);
	OG_mergeUtils.createOutputDirectory(newOG);
	ScwLis scwLis = new ScwLis(scwLisName);
	scwNums = scwLis.getScwIDs();
 	//ArrayList myFileArrayList = recursivelyListFiles(dataDirNames);
 	ArrayList[] results = OG_mergeUtils.getImageFileArrayListFromScwNums(scwNums, dataDirNames);
	ArrayList selectedImageFilesArrayList = results[0];
	ArrayList selectedSwgIdxFilesArrayList = results[1];
	ArrayList selectedDirsArrayList = results[2];
	ArrayList selectedScwNumsArrayList = results[3];
	if ( selectedImageFilesArrayList.size() == 0 ) {
	    logger.warn("No image found");
	}
	else {
	    OG_mergeUtils.constructNewOGStructure(newOG_obs_myobs, selectedDirsArrayList);
	    constructNewSwgIdxFile(selectedSwgIdxFilesArrayList, selectedScwNumsArrayList);
	    prepare_og_ibis();
	    logger.info("Task OG_merge completed");
	}
    }

    //  LOGGER
    public static Logger logger  = Logger.getLogger(OG_merge.class);
    private static File loggerFile;
    private static void configureLogger() throws IOException {
	String loggerFilename= "logger.config";
	InputStream log = ClassLoader.getSystemResourceAsStream(loggerFilename);
	UUID uuid = UUID.randomUUID();
	String homeDir = System.getProperty("user.home");
	loggerFilename = new String(homeDir+File.pathSeparator+"logger.config_"+uuid.toString());
	loggerFile = new File(loggerFilename);
	loggerFile.deleteOnExit();
	inputStreamToFile(log, loggerFilename);
        PropertyConfigurator.configure(loggerFilename);
    }
    public static void inputStreamToFile(InputStream io, String fileName) throws IOException {
	FileOutputStream fos = new FileOutputStream(fileName);
	byte[] buf = new byte[256];
	int read = 0;
	while ((read = io.read(buf)) > 0) {
	    fos.write(buf, 0, read);
	}
	fos.flush();
	fos.close();
    }

    public static void handleArguments(String[] args) throws IOException {
	if ( args.length < 3 ) {
	    logger.info("Usage: java -jar OG_merge-"+version+".jar scw.lis newOG path/to/data1 ... path/to/dataN");
	    System.exit(-1);
	}
	else {
	    scwLisName  = args[0];
	    //logger.info("List of scw is = "+args[0]);
	    newOG = (new File(args[1])).getCanonicalPath();
	    //logger.info("New OG name is = "+newOG);
	    dataDirNames = new String[args.length - 2];
	    for ( int i=0; i < args.length - 2; i++ ) {
		dataDirNames[i] = (new File(args[i+2])).getCanonicalPath();		
	    }
	    Arrays.sort(dataDirNames);
	    logger.info("Running OG_merge "+version);
	}
	newOG_obs_myobs = newOG + OG_mergeUtils.sep +"obs"+ OG_mergeUtils.sep +"myobs"; 
    }

    // openFits
    public static Fits openFits(File file) throws IOException, FileNotFoundException, FitsException  {
	boolean isGzipped = MyFile.isGzipped(file);
	BufferedDataInputStream dis = new BufferedDataInputStream(new FileInputStream(file));
	Fits fitsFile = new Fits(dis, isGzipped);
	return fitsFile;
    }
    public static Fits openFits(String filename) throws IOException, FileNotFoundException, FitsException  {
	return openFits(new File(filename));
    }

    // prepare_og_ibis
    public static void prepare_og_ibis() throws FitsException, IOException {
	String filename = "og_ibis.fits";
	InputStream is = ClassLoader.getSystemResourceAsStream(filename);
	File new_og_ibis = new File(newOG_obs_myobs + OG_mergeUtils.sep + "og_ibis.fits");
	filename = new_og_ibis.getCanonicalPath();
	inputStreamToFile(is, filename);
	//  Modify relevant keywords in the header
	FitsFactory.setUseAsciiTables(false);
	BufferedFile bf = new BufferedFile(filename, "rw");
	Fits f = new Fits(filename);
	BinaryTableHDU srcHDU = (BinaryTableHDU) f.getHDU(1);
	srcHDU.addValue("DATE", today, " Creation or modification date");
	srcHDU.addValue("TSTART", tstart, "Start time of the observation");
	srcHDU.addValue("TSTOP", tstop, "End time of the observation");
	srcHDU.addValue("TELAPSE", telapse, "[s] Total observation elapsed time");
	srcHDU.addValue("OGID", "myobs", "Name of OG directory");
	srcHDU.addValue("PURPOSE", "mosaic", "Scientific purpose of this group");
	srcHDU.addValue("STAMP", today+" OG_merge.java (by G.Belanger)","");

	f.write(bf);
	bf.close();
    }

    public static void fitsMerge (ArrayList selectedFilesList, String mergedFilename) throws IOException, FileNotFoundException, FitsException {
	String[] selectedFilepaths = new String[selectedFilesList.size()];
	for ( int j=0; j < selectedFilesList.size(); j++ ) {
	    selectedFilepaths[j] = ((MyFile) selectedFilesList.get(j)).getCanonicalPath();
	}
	fitsMerge(selectedFilepaths, mergedFilename);
    }

    public static void fitsMerge(String[] filenames, String outFilename) throws IOException, FileNotFoundException, FitsException {
	//  Create input stream and corresponding Fits object with 1st input file
	Fits f1 = openFits(filenames[0]);
	//  Create output stream and corresponding Fits object with same primary header as 1st input file
	BufferedDataOutputStream dos = new BufferedDataOutputStream(new FileOutputStream(outFilename));		
	FitsFactory.setUseAsciiTables(false);
	Fits fout = new Fits();
	fout.addHDU(f1.getHDU(0));
	// Extract the binary table from 1st input file
	BinaryTableHDU hdu1 = (BinaryTableHDU) f1.getHDU(1);
	BinaryTable t1 = (BinaryTable) hdu1.getData();
	// Merge subsequent input files with the 1st
	int nRowsTot = t1.getNRows();
	for ( int j=1; j < filenames.length; j++ ) {
	    Fits f = openFits(filenames[j]);
	    // Append each row of the second and subsequent input files 
	    // at the end of the 1st input file
	    BinaryTable t_temp = (BinaryTable) f.getHDU(1).getData();
	    nRowsTot = nRowsTot + t_temp.getNRows();
	    for ( int k=0; k < t_temp.getNRows(); k++ ) t1.addRow(t_temp.getRow(k));
	    f.getStream().close();
	}
	//  Modify input header for use as header to output file
	Header h2 = hdu1.getHeader();
	h2.addValue("NAXIS2", nRowsTot, " number of rows in table");
	h2.addValue("DATE", today, " Creation or modification date");
	h2.addValue("ORIGIN", "ESA, ESAC, Spain", " Origin of FITS file");
	String programName = "OG_merge";
	h2.addValue("CREATOR", programName, " Program written in JAVA by G. Belanger");
	String programVersion = version;
	h2.addValue("VERSION", programVersion, programName+" version");
	h2.addValue("LOCATN", "ESA, ESAC, Spain", " Site or institution delivering this file");
	h2.addValue("RESPONSI", "gbelanger@sciops.esa.int", " E-mail address of contact person"); 	    
	// Construct output binary table HDU with the modififed header and merged table
	// Then add this HDU to fout and write to output stream
	BinaryTableHDU hdu2 = new BinaryTableHDU(h2,t1);
	fout.addHDU(hdu2);
	fout.write(dos);
    }

    public static MyFile mergeSwgIdxFiles(ArrayList selectedSwgIdxFilesArrayList, String swgIdxMergedFilename) throws Exception {
	logger.info("Merging index files ...");
//  	for ( int j=0; j < selectedSwgIdxFilesArrayList.size(); j++ ) {
//  	    logger.info("file "+(j+1)+": "+((File)selectedSwgIdxFilesArrayList.get(j)).getPath());
//  	}
	fitsMerge(selectedSwgIdxFilesArrayList, swgIdxMergedFilename);
	MyFile swgIdxMerged = new MyFile(swgIdxMergedFilename);
	int mergedRows = ( (BinaryTableHDU) (new Fits(swgIdxMerged)).getHDU(1) ).getNRows();
	logger.info("  Merging complete. Total of "+mergedRows+" rows");
	String outfilename=swgIdxMerged.getParent() +OG_mergeUtils.sep+ "merged-swg_idx_ibis.fits";
	MyFile.copyFile(swgIdxMerged, new MyFile(outfilename));
	return swgIdxMerged;
    }

    public static int[] selectRows(MyFile swgIdxMerged, ArrayList selectedScwNumsArrayList) throws Exception {
	logger.info("Building new swg_idx_ibis.fits:");
	String[] scwNumsLinkNames = new String[selectedScwNumsArrayList.size()];
	for ( int j=0; j < scwNumsLinkNames.length; j++ ) {
	    String scwNum = (String)selectedScwNumsArrayList.get(j);
	    scwNumsLinkNames[j] = "scw" + OG_mergeUtils.sep + scwNum+".001" + OG_mergeUtils.sep + "swg_ibis.fits";
	    //logger.info("  "+scwNumsLinkNames[j]+" was selected");
	}
	int[] selectedRowsIndex = selectFitsRows(swgIdxMerged.getCanonicalPath(), "MEMBER_LOCATION", scwNumsLinkNames);
	int nSelectedRows = selectedRowsIndex.length;
	logger.info("  1) Selected "+nSelectedRows+" rows");
	return selectedRowsIndex;
    }


    /**
     *   Select and return an index of the rows that correspond to all the linkNamesToFind
     *
     *   @param srcFilename String specifying the name of the source FITS file 
     *   @param colNameToSearch String specifying the name of the column to search
     *   @param linkNamesToFind String[] containing the names of the specific column entries to find
     *   @return int[] corresponding to the index of the selected rows
     *   @author Guillaume Belanger <gbelanger@sciops.esa.int
     **/
    public static int[] selectFitsRows(String srcFilename, String colNameToSearch, String[] linkNamesToFind) throws Exception {
	// Create input stream and Fits object using input filename 'srcFilename'
	FitsFactory.setUseAsciiTables(false);
	Fits f = openFits(srcFilename);
	// Get all the link names from the FITS file
	BinaryTableHDU srcHDU = (BinaryTableHDU) f.getHDU(1);
	int nSrcRows = srcHDU.getNRows();
	int col = srcHDU.findColumn(colNameToSearch);
	String[] srcData = (String[]) srcHDU.getColumn(col);
	//  Search for the links we want to find
	int nLinksToFind = linkNamesToFind.length;
	IntArrayList rowIndexes = new IntArrayList();
	for ( int i=0; i < nLinksToFind; i ++ ) {
	    String linkName = linkNamesToFind[i];
	    int index = Arrays.binarySearch(srcData, linkName);
	    if ( index >= 0 ) rowIndexes.add(index);
	    else { logger.warn("String not found: "+linkName); }
	}
	rowIndexes.trimToSize();
	rowIndexes.sort();
	return rowIndexes.elements();
    }

    public static void deleteUnselectedRows(MyFile swgIdxMerged, int[] selectedRowsIndex) throws IOException, FitsException {
	FitsFactory.setUseAsciiTables(false);
	BufferedFile bf = new BufferedFile(swgIdxMerged.getCanonicalPath(), "rw");
	Fits f = new Fits(swgIdxMerged.getCanonicalPath());
	BinaryTableHDU srcHDU = (BinaryTableHDU) f.getHDU(1);
	int nSelectedRows = selectedRowsIndex.length;
	int mergedRows = ( (BinaryTableHDU) (new Fits(swgIdxMerged)).getHDU(1) ).getNRows();
	int nTotalRowsToDel = mergedRows - nSelectedRows;
	int newTotalNumOfRows = mergedRows;
	int shift = 0;
	int nDeletedRows = 0;
	int row = 0;
	int element = 0;
	int first = 0;
	//  Delete rows before first selected index
	int r = 0;
	int index = selectedRowsIndex[r];
	int nextIndex = selectedRowsIndex[r+1];;
	int nRowsToDel = index;
	if ( index > 0 ) {
	    srcHDU.deleteRows(0, nRowsToDel);
	    shift += nRowsToDel;
	    nDeletedRows += nRowsToDel;
	    newTotalNumOfRows -= nRowsToDel;
  	    logger.debug("index = "+index+" and nextIndex = "+nextIndex+" (diff="+(nextIndex-index)+")");
  	    logger.debug("Deleting "+nRowsToDel+" rows starting from new row "+row);
  	    logger.debug("Total deleted = "+nDeletedRows);
	}
	//  Delete rows up to the last selected row
	while ( r < nSelectedRows-1 ) {
	    //System.out.println(selectedRowsIndex[r]);
	    index = selectedRowsIndex[r];
	    nextIndex = selectedRowsIndex[r+1];
	    nRowsToDel = nextIndex - index -1;
	    if ( nRowsToDel > 0 ) {
		row = index+1 - shift;
//  		logger.debug("index = "+index+" and nextIndex = "+nextIndex+" (diff="+(nextIndex-index)+")");
//  		logger.debug("Deleting "+nRowsToDel+" rows starting from new row "+row);
		srcHDU.deleteRows(row, nRowsToDel);
		nDeletedRows += nRowsToDel;
		newTotalNumOfRows -= nRowsToDel;
		shift += nRowsToDel;
//  		logger.debug("Total deleted = "+nDeletedRows);
	    }
	    r++;
	}
	//  Delete everything after the last selected row
	if ( nDeletedRows < nTotalRowsToDel ) {
	    index = selectedRowsIndex[selectedRowsIndex.length-1];
	    row = index+1 - shift;
	    nRowsToDel = nTotalRowsToDel - nDeletedRows;
// 	    logger.debug("last selected index = "+index);
// 	    logger.debug("Deleting "+nRowsToDel+" rows starting from new row "+row);
	    srcHDU.deleteRows(row, nRowsToDel);
	    nDeletedRows += nRowsToDel;
//  	    logger.debug("Total deleted = "+nDeletedRows);
	}
	logger.info("  2) Deleted "+nDeletedRows+" extra rows");
	f.write(bf);
	bf.close();
	//  Print out some info about the newly merged index file
	MyFile final_swgIdx = swgIdxMerged;
	int newRows = ((BinaryTableHDU) (new Fits(final_swgIdx)).getHDU(1)).getNRows();
	logger.info("  New swg_idx_ibis.fits ready with "+newRows+" rows");
// 	dis.close();
    }

    private static void constructNewSwgIdxFile(ArrayList selectedSwgIdxFilesArrayList, ArrayList selectedScwNumsArrayList) throws Exception {
	String swgIdxMergedFilename = newOG_obs_myobs + OG_mergeUtils.sep + "swg_idx_ibis.fits";
	MyFile swgIdxMerged = mergeSwgIdxFiles(selectedSwgIdxFilesArrayList, swgIdxMergedFilename);
	int[] selectedRowsIndex = selectRows(swgIdxMerged, selectedScwNumsArrayList);
	deleteUnselectedRows(swgIdxMerged, selectedRowsIndex);
	defineTStartTStopTElapse(swgIdxMerged.getCanonicalPath());
    }

    private static void defineTStartTStopTElapse(String swgIdxFilename) throws Exception {
	Fits f = openFits(swgIdxFilename);
	BinaryTableHDU hdu = (BinaryTableHDU) f.getHDU(1);
	double[] tstart_mjd = (double[]) hdu.getColumn("TSTART");
	double[] tstop_mjd = (double[]) hdu.getColumn("TSTOP");
	double[] telapse_s = (double[]) hdu.getColumn("TELAPSE");
	tstart = tstart_mjd[0];
	tstop = tstop_mjd[tstop_mjd.length-1];
	telapse = OG_mergeUtils.getSum(telapse_s);
    }
 

}
