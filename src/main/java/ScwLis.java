import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;

public class ScwLis {
    
    // Class variables
    private InputStream str;
    private int nScws = 0;
    public String[] scwIDs = null;
    private boolean scwIDsAreSet = false;
    public static String sep = File.separator;
    private int bufferSize = 8 * 1024;

    // Constructors
    public ScwLis() {
    }

    public ScwLis(String filename) throws IntegralException, IOException  {
	File file = new File(filename);
	if ( ! file.exists() ) {
	    throw new IntegralException("File does not exist");
	}
	else {
	    readScwFile(file);
	}	    
    }

    private void readScwFile(File file) throws FileNotFoundException {
	Scanner in = new Scanner(new FileReader(file));
	ArrayList scwIDsArrayList = new ArrayList();
	while ( in.hasNextLine() ) {
	    String line = in.nextLine();
	    String scwID = line.substring(9,21);
	    scwIDsArrayList.add(scwID);
	}
	scwIDsArrayList.trimToSize();
	Collections.sort(scwIDsArrayList);
	this.nScws = scwIDsArrayList.size();
	this.scwIDs = new String[nScws];
	for ( int i=0; i < this.nScws; i++ ) {
	    this.scwIDs[i] = (String) scwIDsArrayList.get(i);
	}
	this.scwIDsAreSet = true;	
    }

    //  other methods
    public int nScws() {
	return this.nScws;
    }

    public String[] getScwIDs() throws IntegralException {
	if ( this.scwIDsAreSet ) {
	    return Arrays.copyOf(this.scwIDs, this.scwIDs.length);
	}
	else {
	    throw new IntegralException("Scw IDs are not set");
	}
    }

    public void setScwIDs(String[] scwIDs) throws IOException {
	this.scwIDs = Arrays.copyOf(scwIDs, scwIDs.length);
	this.nScws = this.scwIDs.length;
	this.scwIDsAreSet = true;
    }

    public void write(String filename) throws IOException, IntegralException {
	if ( ! this.scwIDsAreSet ) throw new IntegralException("Scw IDs are not set: there is nothing to write");
	File file = new File(filename);
	PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file), bufferSize));
	String rev = null;
	String line = null;
	String sufix = null;
	for ( int i=0; i < scwIDs.length; i++ ) {
	    rev = scwIDs[i].substring(0,4);
	    sufix = "swg.fits[1]";
	    line = "scw" +sep+ rev +sep+ scwIDs[i]+ ".001" +sep+ sufix;
	    pw.println(line);
	}
	pw.flush(); 
	pw.close();
    }


}
