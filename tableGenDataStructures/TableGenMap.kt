package tableGenDataStructures

/** Dealing with pairs of arrays can be slightly annoying at times.
 * Let's make a class that'll make it a bit easier for us.
 * Representation invariant: `boards` is sorted in ascending order. */
class TableGenMap(val boards:LongArray){
  val probabilities=LongArray(boards.size){_->0L}
  /** map.get(board) is the board probability associated with that board. |
   * Precondition: There exists an index `i` where `boards[i]==board . */
  fun get(board:Long) = probabilities[binarySearch(board,boards)]
  companion object{
    /** `binarySearch(target,array)` is the index `i` where `array[i]==target`.
     * Preconditions:
     * 1. `array` is sorted in ascending order.
     * 2. There exists an index `i` where `array[i]==target`. */
    private fun binarySearch(target:Long,array:LongArray):Int{
      var min=0
      var max=array.size-1
      if(array[min]==target) return 0//min
      if(array[max]==target) return max
      while(true){//Binary search; note that we only need to compare `mid`
        val mid=(min+max) shr 1
        if(array[mid]==target) return mid
        else if(array[mid]<target) min=mid
        else if(array[mid]>target) max=mid
      }
    }
  }
}