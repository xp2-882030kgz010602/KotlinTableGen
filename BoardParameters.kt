/** An unencapsulated class storing parameters that specify board size and movement. */
class BoardParameters(val boardSize:Int,val movesMovable:Array<Array<IntArray>>,val movesImmovable:Array<Array<IntArray>>)
  //If `moveNames[i]` is the name of a given move,
  //then `movesMovable[i]` describes which columns can move,
  //and `movesImmovable[i]` describes which columns can't.
