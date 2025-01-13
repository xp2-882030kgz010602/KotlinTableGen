package board
/** A board with `n` tiles is represented as a list of tiles in reverse order within a single long.
 * Each tile can be blank, or a power of 2 ranging from 2 to 32768.
 * Each tile is given 4 bits of space, with 0 representing a blank space
 * and k>0 representing a tile of value 2^k.
 * Unused bits are filled with zeros. |
 * Example: The following board has the board representation of `0x4def3abc27891231`,
 * if tiles are taken in standard reading order.
 *`2   8   4   2 |
 * 512 256 128 4 |
 * 4k  2k  1k  8 |
 * 32k 16k 8k  16` */
class BoardHelper{
  companion object{
    //Note that the assumption of 4 bits per tile is in a lot of different parts of this program.
    //Changing `getShift on its own will break a lot of other things.
    //This assumption also exists in BoardMovement.moveBoard, BoardIO.getByteSize, and TableGenTree,
    //unless it also exists somewhere else and I forgot/didn't realize.
    //Also note that it is currently pointless to try increasing the size, since you only need 5+ bits on boards with
    //at least 15 tiles, and 5*15=75>64, which won't fit in a Long. As a result, I think that as things currently are,
    //it is not worth putting "4" into its own separate parameter. If a 128-bit integer or whatever becomes a primitive data type
    //with hardware support, *then* I might consider it.
    /** `getShift(index)` is the shift corresponding to the tile index of that board. */
    //Shift 4 bits per tile
  //fun getShift(index:Int):Int = index*4
    fun getShift(index:Int):Int = index shl 2
    /** `getTile(board,index)` is the tile at the given index in the board. */
    fun getTile(board:Long,index:Int):Long = (board shr getShift(index)) and 15
    /** `setTile(board,index,tile)` is the board with the tile at that index
     * changed to the given tile. */
    fun setTile(board:Long,index:Int,tile:Long):Long{
      val shift=getShift(index)
      var boardModified=board
      boardModified=boardModified and (15L shl shift).inv()//Clear the old tile
      return boardModified xor (tile shl shift)//Put in the new tile
    }
    /** `tileToValue(tile)` is the value of the tile given. */
    private fun tileToValue(tile:Long):Int = if(tile==0L) 0 else 1 shl tile.toInt()
    /** Calls `function(index,tile)` on each tile in `board`. */
    fun iterateTiles(board:Long,boardSize:Int,function:(Int,Long)->Unit){
      var brd=board
      for(i in 0..<boardSize){
        function(i,brd and 15L)
        brd=brd shr 4
      }
    }
    /** `getTileSum(board,boardSize)` is the sum of the values of the tiles in the given board. */
    fun getTileSum(board:Long,boardSize:Int):Int{
      var sum=0
      iterateTiles(board,boardSize){_,tile->sum+=tileToValue(tile)}
      return sum
    }
    /** `numBlankSpaces(board,boardSize)` is the number of blank spaces in `board`. */
    fun numBlankSpaces(board:Long,boardSize:Int):Int{
      var blankSpaces=0
      iterateTiles(board,boardSize){_,tile->if(tile==0L) blankSpaces+=1}
      return blankSpaces
    }
    /** `getMaxSum(boardSize)` is the maximum possible tile sum of a board with the given `boardSize`.
     * Observe that filling a board with a 4 then an 8 then a 16 and so on yields this sum. */
    fun getMaxSum(boardSize:Int):Int = (4 shl boardSize)-4
  }
}