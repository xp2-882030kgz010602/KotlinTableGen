package tableGenDataStructures

import board.BoardHelper
import java.util.TreeMap
/** This contains "seed" boards that the position lister uses to list out positions.
 * This uses a tile sum->TableGenDataStructures.TableGenTree map to allow fast retrieval by sum. */
class SeedSet(private val boardSize:Int){
  private var seeds=TreeMap<Int,TableGenTree>()
  /** `set.add(board)` adds `board` to the set. */
  fun add(board:Long){
    val sum=BoardHelper.getTileSum(board,boardSize)
    //Initialize this if it hasn't been initialized yet
    if(seeds[sum]==null){
      seeds[sum]=TableGenTree(boardSize)
    }
    seeds[sum]!!.add(board)
  }
  /** `set.get(sum)` is a TableGenDataStructures.TableGenTree containing all the boards in `set` with the given sum. */
  fun get(sum:Int):TableGenTree?{
    return seeds[sum]
  }
}