package tableGenDataStructures

import board.BoardHelper
/** This is a base-16 prefix tree.
 * The last tile of a board "exists in" the root of the tree.
 * Navigating towards leaf nodes reveals the tiles of the tree in reverse order.
 * Remember that tiles in a board representation are stored in reverse order.
 * A TableGenTree that stores boards with n tiles should be initialized with depth n. */
class TableGenTree(private val depth:Int){
  //Don't bother initializing this array on leaf nodes where depth=0
  private val nodes:Array<TableGenTree?>? = if(depth==0) null else arrayOfNulls(16)
  /** `tree.add(board)` adds `board` to `tree`. */
  fun add(board:Long){
    if(nodes==null) return//We're at depth 0 and this is a leaf node
    else{
      val depthNext=depth-1
      val shift=BoardHelper.getShift(depthNext)
      val tile=board shr shift//Get the last tile of the board
      val boardNext=board xor (tile shl shift)//And remove it before navigating up a layer
      //If this node isn't initialized yet, initialize it
      val index=tile.toInt()
      if(nodes[index]==null) nodes[index]=TableGenTree(depthNext)
      //Then just add to the child node
      nodes[index]!!.add(boardNext)//Uh Kotlin
    }
  }
  /** `tree.iterate(0L,func)` applies `func` to each board in `tree`.
   * This is done in ascending order: if we were to say, print out the boards,
   * we would see that they are already sorted.  */
  fun iterate(prefix:Long,function:(Long)->Unit){
    //Leaf node; the prefix is the complete board
    if(nodes==null) function(prefix)
    else{
      //Otherwise, iterate through the child nodes
      var prefixNext=prefix shl 4
      for(i in 0..15){
      //val prefixNext=(prefix shl 4)+i
        nodes[i]?.iterate(prefixNext,function)
        prefixNext+=1
      }
    }
  }
  /** `tree.merge(other)` adds every board in `other` into `tree`. */
  fun merge(other:TableGenTree) = other.iterate(0L){board->add(board)}
}