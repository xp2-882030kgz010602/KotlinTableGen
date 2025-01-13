package board

import tableGenDataStructures.TableGenMap
import java.io.*

typealias StringPair=Pair<String,String>

class BoardIO{
  companion object{
    /** `getPath(directory,sum,suffix)` is the path leading to the file where data is stored
     * for boards with the given sum, plus the given suffix. */
    private fun getPath(directory:String,sum:Int,suffix:String):String = "$directory/$sum$suffix.txt"
    /** `getFile` is like `getPath`, but it instead returns a `File` object. */
    fun getFile(directory:String,sum:Int,suffix:String):File = File(getPath(directory,sum,suffix))
    /** `getWriter` is like `getPath`, but it instead returns a `BufferedOutputStream`. */
    fun getWriter(directory:String,sum:Int,suffix:String):BufferedOutputStream =
      BufferedOutputStream(FileOutputStream(getFile(directory,sum,suffix)))
    /** `getReader` is like `getPath`, but it instead returns a `BufferedInputStream`. */
    fun getReader(directory:String,sum:Int,suffix:String):BufferedInputStream =
      BufferedInputStream(FileInputStream(getFile(directory,sum,suffix)))
    /** `getByteSize(boardSize)` is the number of bytes that a board of the given size would take up in a file. */
    fun getByteSize(boardSize:Int):Int = (boardSize+1) shr 1
    /** `writeData(stream,data,getByteSize)` writes the given data to `stream`. */
    fun writeData(stream:BufferedOutputStream,data:Long,numBytes:Int){
      for(shift in (numBytes-1) shl 3 downTo 0 step 8) stream.write((data shr shift).toInt() and 255)
    }
    /** `readData(stream,getByteSize)` is the next `getByteSize` bytes of the stream represented as a `Long`.
     * If EOF is reached, returns `-1L` instead. */
    fun readData(stream:BufferedInputStream,numBytes:Int):Long{
      var data=0L
      for(i in 1..numBytes){
        data=data shl 8
        //-1L is 0xffffffffffffffff, so using `or` guarantees -1L if -1 is read (reading after EOF returns -1)
        data=data or stream.read().toLong()
      }
      return data
    }
    /** `readBoardList(file,boardSize)` is the board list contained in that file. */
    fun readBoardList(file:File,boardSize:Int):LongArray{
      val boardByteSize=getByteSize(boardSize)
      var numBoards=0
      //First pass: Count the number of boards
      val firstPass=BufferedInputStream(FileInputStream(file))
      while(firstPass.read()!=-1){
        numBoards+=1
        //Throw away the remaining `boardByteSize-1` bytes
      //for(i in 1..boardByteSize-1) firstPass.read()
        for(i in 2..boardByteSize) firstPass.read()
      }
      firstPass.close()
      //Second pass: Read out the boards and save them to the array
      val secondPass=BufferedInputStream(FileInputStream(file))
      val boards=LongArray(numBoards)
      for(i in 0..<numBoards) boards[i]=readData(secondPass,boardByteSize)
      secondPass.close()
      return boards
    }
    /** `iteratePageEntries(paths,sum,boardByteSize,function)` reads out all the entries in the page specified by the given paths and sum,
     * and calls `function(board,probability,index)` on each entry. */
    fun iteratePageEntries(paths:StringPair,sum:Int,boardByteSize:Int,function:(Long,Long,Int)->Unit){
      val listInput=getReader(paths.first,sum,"-list")
      val probabilityInput=getReader(paths.second,sum,"-chance")
      val indexInput=getReader(paths.second,sum,"-index")
      while(true){
        val index=indexInput.read()
        if(index==-1){//We're done
          listInput.close()
          probabilityInput.close()
          indexInput.close()
          return
        }
        val board=readData(listInput,boardByteSize)
        val probability=readData(probabilityInput,7)
        function(board,probability,index)
      }
    }
    /** `readSingleEntry(paths,sum,boardByteSize,board) is the pair consisting of the board probability for that board,
     * and the move index that yields that board probability. Note that this streams the entire page every time this is called.
     * If you are getting data for many boards of the same sum, consider using `fillMap` or `iteratePageEntries` instead.
     * Precondition: The board must be in the given page. If it isn't, a NullPointerException will be thrown. */
    fun readSingleEntry(paths:StringPair,sum:Int,boardByteSize:Int,board:Long):Pair<Long,Int>{
      var probability:Long?=null
      var index:Int?=null
      iteratePageEntries(paths,sum,boardByteSize){brd,thisProbability,thisIndex->
        if(board==brd){
          probability=thisProbability
          index=thisIndex
        }
      }
      return Pair(probability!!,index!!)//This assertion essentially says "this board must be in our table".
    }
    /** Fills in the probabilities for `map`, assuming that the boards are already known. |
     * Precondition: Every board in `map.boards` must be in the given page. */
    fun fillMap(paths:StringPair,sum:Int,boardByteSize:Int,map:TableGenMap){
      val listInput=getReader(paths.first,sum,"-list")
      val probabilityInput=getReader(paths.second,sum,"-chance")
      map.boards.onEachIndexed{i,board->
        while(true){//Usage of ascending order
          val brd=readData(listInput,boardByteSize)
          val probability=readData(probabilityInput,7)
          if(board==brd){//We found the board
            map.probabilities[i]=probability
            break
          }
        }
      }
      listInput.close()
      probabilityInput.close()
    }
    /** `formatTile(tile)` is a string representation of `tile`.
     * Precondition: `0L<=tile<16L` */
    fun formatTile(tile:Int):String =
      if(tile==0) "_"//Blank space
      else if(tile<10) (1 shl tile).toString()//2 to 512
      else if(tile==10) "1k"//1024
      else formatTile(tile-10)+"k"//2048 to 32768
    /** `formatBoard(board,template,spawnIndex)` is a string consisting of the given board formatted to the given template.
     * If `spawnIndex` is provided, the tile at that index will have a "<" placed after it.
     * Preconditions:
     * 1. Any char that is '0'-'9' or 'a'-'d' must not be the last or second-last char in the template,
     * and *should* not be the last or second-last char in any row of the template.
     * Violating the first clause could cause a crash, but the second one might "just" mangle rows instead.
     * 2. If `spawnIndex` is provided, the tile at that index must be at most 64.
     * 3. No chars that are '0'-'0' or 'a'-'d' can be out of bounds of the board size.
     * For example, '8' cannot be used in a template for a board that has less than 9 tiles. */
    fun formatBoard(board:Long,template:String,spawnIndex:Int?):String{
      val chars=template.toCharArray()
      var j=0
      while(j<chars.size){
        val char=chars[j]
        val index=if('0'.code<=char.code&&char.code<='9'.code) char.code-'0'.code//0-9
        else if('a'.code<=char.code&&char.code<'d'.code) char.code-'a'.code+10//a-d
        else{
          j+=1
          continue
        }
        val tileString=formatTile(BoardHelper.getTile(board,index).toInt()) + (if(index==spawnIndex) "<" else "")
        tileString.onEachIndexed{dj,tileChar->chars[j+dj]=tileChar}
        //String representation of tile has up to 3 chars; skip over 3 chars to avoid accidentally "formatting"
        //chars that were the output of previous formatting
        j+=3
      }
      return chars.concatToString()
    }
    /** `formatProbability(probability)` is a string representation of `probability.
     * This is similar to `probability.toString()`, but it pads to the left with spaces to guarantee a 17-character string. */
    fun formatProbability(probability:Long):String{
      var probabilityString=probability.toString()
      while(probabilityString.length<17) probabilityString=" $probabilityString"//Pad spaces as necessary
      return probabilityString
    }

  }
}