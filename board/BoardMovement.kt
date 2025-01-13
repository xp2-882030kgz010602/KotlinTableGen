package board

class BoardMovement{
  companion object{
    /** `moveColumn(board,columnIndices)` is a modified version of `board`,
     * where tiles have been moved along the column specified by `columnIndices`.
     * Tiles move towards the position specified by the first position of the column. |
     * For example: |
     * `moveColumn(0x1123,[0,1,2,3])==0x0223` |
     * `moveColumn(0x1123,[3,2,1,0])==0x2230` |
     * See movementnotes.txt for description of algorithm. */
    private fun moveColumn(board:Long,columnIndices:IntArray):Long{
      var register=0L
      //This is a "meta-index" that points to an index in `columnIndices`. The top head points to the `topIndexIndex`th tile of `brd`.
      var topIndexIndex=0
      var brd=board
      for(bottomIndex in columnIndices){
        val shiftBottom=BoardHelper.getShift(bottomIndex)
        //Read out tile and clear it from the board
      //val readout=board.BoardHelper.getTile(brd,bottomIndex)
      //brd=board.BoardHelper.setTile(brd,bottomIndex,0L)
        var readout=brd and (15L shl shiftBottom)
        //If rule 2 is satisfied, then we don't have to finish the readout process since it's all zeroes already
        if(readout==0L) continue
        brd=brd xor readout
        //Then shift back so that we can write into register
        readout=readout shr shiftBottom
        if(register==0L){//Rule 0
          register=readout
          continue
        }
        //If we've made it here, we know that both the register and readout are nonzero,
        //so we know we are definitely writing a tile to the board.
        val shiftTop=BoardHelper.getShift(columnIndices[topIndexIndex])
        topIndexIndex+=1
        //We don't have to worry about wiping the old tile because the bottom head has already cleared it
        if(readout==register){//Rule 1
          brd=brd xor ((register+1) shl shiftTop)
          register=0
        }else{//Rule 3
          brd=brd xor (register shl shiftTop)
          register=readout
        }
      }
      //Finish by copying the register back onto the board one more time
      val shiftTop=BoardHelper.getShift(columnIndices[topIndexIndex])
      brd=brd xor (register shl shiftTop)
      return brd
    }
    /** `isLocked(board,columnIndices)` is `true` if no movement is possible in the column in either direction.
     * This is equivalent to but faster than checking
     * `moveColumn(board,columnIndices)==board && moveColumn(board,columnIndices.reversed())==board`.
     * For example, if you had a board like `X ? ? ?` and didn't want to move the X tile,
     * then when moving right, you could check `isLocked(...,[0,1,2])`,
     * assuming that you are indexing tiles in reading order.
     * When moving left, you wouldn't have to check at all,
     * since the `X` tile wouldn't be able to move at all in that case. |
     * Examples: |
     * `isLocked(0x1123L,[0,1,2,3])==false` |
     * `isLocked(0x1212L,[0,1,2,3])==true` |
     * `isLocked(0x0212L,[0,1,2,3])==false` | */
    private fun isLocked(board:Long,columnIndices:IntArray):Boolean{
      //We simply need to check that there is no `i` where the `i`th tile is blank,
      //and no `i` where the `i`th and the `i+1`th tile are equal.
      var left=-1L//Just initialize as something that can't actually show up as a tile
      for(index in columnIndices){
        val right=BoardHelper.getTile(board,index)
        //Check zero-ness and equality
        if(right==0L||right==left) return false
        left=right
      }
      return true
    }
    /** `moveBoard(board,movable,immovable)` is `board` if the move is invalid
     * (immovable columns would move or no tiles would move),
     * or the result of moving among all columns specified by `movable` otherwise. */
    fun moveBoard(board:Long,movable:Array<IntArray>,immovable:Array<IntArray>):Long{
      //Check if the required columns are locked
      //Can't use `Array.any` because that returns true on an empty array
      for(immovableColumnIndices in immovable) if(!isLocked(board,immovableColumnIndices)) return board
      //Move on each column
      return movable.fold(board){brd,movableColumnIndices->moveColumn(brd,movableColumnIndices)}
    }
    /** `iterateSpawns(board,boardSize,twoFunction,fourFunction)` iterates over all boards
     * that can result from a tile spawning from a blank space in `board`,
     * applying `twoFunction` if a 2 spawns, and applying `fourFunction` if a 4 spawns.
     * The inputs that `twoFunction` and `fourFunction` are run on are sorted in ascending order.
     * In particular, this means that calls are made to `twoFunction` and `fourFunction` in alternating order.
     * If you were to print out the resulting boards, they would already be sorted. */
    fun iterateSpawns(board:Long,boardSize:Int,twoFunction:(Long)->Unit,fourFunction:(Long)->Unit){
      var spawnValue=1L
      var mask=15L
      for(i in 0..<boardSize){
        //val spawnValue=1L shl board.BoardHelper.getShift(i)
        //val mask=spawnValue*15L
        if(board and mask == 0L){
          //We know these 4 bits are zero, so xor and plus are the same, but xor apparently uses less energy
          val plusTwo=board xor spawnValue
          twoFunction(plusTwo)
          val plusFour=plusTwo+spawnValue
          fourFunction(plusFour)
        }
        spawnValue=spawnValue shl 4
        mask=mask shl 4
      }
    }
    /** Calling `iterateMoves(board,movesMovable,movesImmovable,callback)` attempts for each `i` to perform the move
     * specified by `movesMovable[i]` and `movesImmovable[i]`. If the move is possible, then `callback(i,boardMoved)`
     * is called, where `boardMoved` is the result of performing the move. |
     * Precondition: `movesMovable.size==movesImmovable.size` */
    fun iterateMoves(board:Long,movesMovable:Array<Array<IntArray>>,movesImmovable:Array<Array<IntArray>>,callback:(Int,Long)->Unit){
      //I admit that "movesMovable" and "movesImmovable" are awkward variable names, but I don't have anything better right now.
      for(i in movesMovable.indices){
        val movable=movesMovable[i]
        val immovable=movesImmovable[i]
        val boardMoved=moveBoard(board,movable,immovable)
        if (board!=boardMoved) callback(i,boardMoved)
      }
    }
  }
}