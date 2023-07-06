package edu.jhuapl.sbmt.pointing;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * @author steelrj1
 *
 */
public class StateHistoryUtil
{

	 // binary searches the binary file for a time for the new time interval feature - Alex W
    /**
     * @param first
     * @param last
     * @param target
     * @param pos
     * @param lineLength
     * @param path
     * @return
     */
    public static int binarySearch(int first, int last, String target, boolean pos, int lineLength, File path)
    {
        if(first > last)
        {
            if(pos)
            {
                return (last + 1) * lineLength;
            }
            return (last) * lineLength;
        }
        else
        {
            int middle = (first+last)/2;
            int compResult = target.compareTo(readString((middle) * lineLength, path));
            if(compResult == 0)
                return (middle) * lineLength;
            else if(compResult < 0)
                return binarySearch(first, middle - 1, target, pos, lineLength, path);
            else
                return binarySearch(middle + 1, last, target, pos, lineLength, path);
        }
    }

    // gets the number of lines of a binary file, needed for the binary search - Alex W
    /**
     * @param path
     * @param lineLength
     * @return
     */
    public static long getBinaryFileLength(File path, int lineLength)
    {
        long length = 0;
        try
        {
            RandomAccessFile fileStore = new RandomAccessFile(path, "r");
            length = fileStore.length()/lineLength;
            fileStore.close();
        }
        catch (Exception e)
        {
            return length;
        }
        return length;
    }

    // reads binary that represents a String - Alex W
    /**
     * @param postion
     * @param path
     * @return
     */
    public static String readString(int postion, File path)
    {
        String string = "";
        try
        {
            RandomAccessFile fileStore = new RandomAccessFile(path, "r");
            fileStore.seek(postion);
            string = fileStore.readUTF();
            fileStore.close();
        }
        catch (Exception e)
        {
            return "";
        }
        return string;
    }

    // reads binary that represents a double - Alex W
    /**
     * @param postion
     * @param path
     * @return
     */
    public static double readBinary(int postion, File path)
    {
        double num = 0;
        try
        {
            RandomAccessFile fileStore = new RandomAccessFile(path, "r");
            fileStore.seek(postion);
            num = fileStore.readDouble();
            fileStore.close();
        }
        catch (Exception e)
        {
            return 0;
        }
        return  num;
    }

}
