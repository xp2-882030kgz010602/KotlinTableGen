The successor to https://github.com/xp2-882030kgz010602/tablegen.
I *hope* that the code is actually readable this time.

This table generator gives you the ability to specify whatever boards you want, with up to 14 tiles and 255 moves,
and table generation and AI play on any such board, optimized to satisfy any target function.
This isn't restricted to boards that could "physically exist";
moves can have tiles slide in ways that make no sense whatsoever.
For example, you could have a 3x3 board where tiles in the center row/column
move the "wrong way", or you could have a 3x3 board with a hole in the center,
where tiles will "teleport" across this hole.
Motion on the two boards specified would look like this.
```
First example
2 2 2       _ 2 4       _ 4 _
2 2 2  R->  4 2 _  D->  _ 2 _
2 2 2       _ 2 4       4 _ 8

Second example
2 2 2       _ 2 4       _ _ _
2   2  R->  _   4  D->  _   4
2 2 2       _ 2 4       _ 4 8
```
Another limitation that needs to be kept in mind with larger boards is that
pages (files containing boards with a given tile sum and data for said boards)
cannot have data for more than 2^31-1 boards. I cannot do much about this,
because Kotlin arrays can only be indexed by `Int`s.

For larger tables, I heavily recommend using IntelliJ's "build to jar" option.
There is a good tutorial on it here. https://www.jetbrains.com/help/idea/create-your-first-kotlin-app.html?section=IntelliJ%20build%20tool
Both KotlinTableGen and IntelliJ are HUNGRY for resources, and being able to
close out IntelliJ before running KotlinTableGen can make a real difference,
and if IntelliJ freezes while running KotlinTableGen, you are not going to be able to check the output.
Running KotlinTableGen outside of IntelliJ avoids all of this. I have never seen a command prompt freeze.

Now, let's look at some examples.

First, let's make a table for and have an AI play on a small 6-tile board.
```kotlin
import tableGenDataStructures.SeedSet

fun main(){
  //Specify a board that looks like this (DPDF6 in 3x3)
  //0 1 2
  //X 5 3
  //X X 4
  val boardSize=6
  val movesMovable=Array<Array<IntArray>>(4){_->arrayOf()}
  movesMovable[0]=arrayOf(intArrayOf(0,1,2),intArrayOf(5,3))//L
  movesMovable[1]=arrayOf(intArrayOf(4,3,2),intArrayOf(5,1))//D
  movesMovable[2]=arrayOf(intArrayOf(2,1,0))//R
  movesMovable[3]=arrayOf(intArrayOf(2,3,4))//U
  val movesImmovable=Array<Array<IntArray>>(4){_->arrayOf()}
  movesImmovable[2]=arrayOf(intArrayOf(4),intArrayOf(3,5))//R
  movesImmovable[3]=arrayOf(intArrayOf(0),intArrayOf(1,5))//U
  val boardParameters=BoardParameters(boardSize,movesMovable,movesImmovable)

  //Create a list from the initial position of
  //_ 2 _
  //X _ _
  //X X _
  val seedsA=SeedSet(boardSize)
  seedsA.add(0x000010L)
  Kernel.listPositions(boardParameters,"triangle3_a",seedsA,null)

  //Create a list from the initial position of
  //_ _ 2
  //X _ _
  //X X _
  //and include boards from the first list
  val seedsB=SeedSet(boardSize)
  seedsB.add(0x000100L)
  Kernel.listPositions(boardParameters,"triangle3_b",seedsB,"triangle3_a")

  //A target function that looks for a 128 tile anywhere in the board
  fun target(board:Long):Boolean{
    var brd=board
    for(i in 1..boardSize){
      if((brd and 15L) == 7L) return true
      brd=brd shr 4
    }
    return false
  }

  //Create tables from the first list
  Kernel.makeTables(boardParameters,Kernel.pairSelf("triangle3_a"),null){brd->target(brd)}

  //Create tables from the second list
  Kernel.makeTables(boardParameters,Pair("triangle3_b","triangle3_c"),null){brd->target(brd)}

  //Create tables from the second list, but include entries from the first table whenever possible, saving computing time
  Kernel.makeTables(boardParameters,Pair("triangle3_b","triangle3_d"),Pair("triangle3_b","triangle3_c")){brd->target(brd)}

  //Play a game using the second set of tables
  val template="0   1   2  \nX   5   3  \nX   X   4  "
  val moveNames=arrayOf("L","D","R","U")
  val initialBoard=0x000100L
  Kernel.playAIGame(boardParameters,moveNames,Pair("triangle3_b","triangle3_d"),initialBoard,template,true)

  //Display move probabilities for a board in the table
  Kernel.displayMoveProbabilities(boardParameters,moveNames,Pair("triangle3_b","triangle3_d"),0x622101L)
}
```
Now, let's try making a table for a 2x4 board. Since this board is rectangular,
we can automatically generate board specifications and formatting templates.
Once again, the AI can play.
```kotlin
import board.BoardHelper
import tableGenDataStructures.SeedSet

fun main(){
  //Create a 2x4 board
  //0 1 2 3
  //4 5 6 7
  val boardParameters=Kernel.makeRectangleBoardSpecification(4,2)

  //Initialize with all possible starting boards
  val seeds=SeedSet(boardParameters.boardSize)
  for(i in 0..<boardParameters.boardSize-1){
    val first=1L shl BoardHelper.getShift(i)
    for(j in i+1..<boardParameters.boardSize){
      val second=1L shl BoardHelper.getShift(j)
      //This adds boards with every combination of 2s and 4s at the given indices
      for(a in 0..1) for(b in 0..1) seeds.add((first shl a) xor (second shl b))
    }
  }

  Kernel.listPositions(boardParameters,"2x4",seeds,null)

  fun target(brd:Long):Boolean{
    val maxSum=BoardHelper.getMaxSum(boardParameters.boardSize)
    val sum=BoardHelper.getTileSum(brd,boardParameters.boardSize)
    return sum==maxSum
  }
  Kernel.makeTables(boardParameters,Kernel.pairSelf("2x4"),null){brd->target(brd)}

  val template=Kernel.makeRectangleBoardTemplate(4,2)
  val moveNames=arrayOf("L","R","D","U")
  //Having the last parameter as false means that probabilities for moves other than the optimal move aren't shown
  Kernel.playAIGame(boardParameters,moveNames,Kernel.pairSelf("2x4"),0x10000001L,template,false)
}
```
Finally, let's try a 2x2x2 cube for a board. Despite the fact that three dimensions are needed to "properly" draw this board,
we can still generate tables and see AI play on this board. Also of note is that this board has 6 moves instead of 4.
```kotlin
import board.BoardHelper
import tableGenDataStructures.SeedSet

fun main(){
  //Create a 2x2x2 board
  //0 1   4 5
  //2 3   6 7
  val boardSize=8
  val movesMovable=Array<Array<IntArray>>(6){_->arrayOf()}
  movesMovable[0]=Array(4){i->val j=2*i;intArrayOf(j,j+1)}//[[0,1],[2,3],[4,5],[6,7]] (L)
  movesMovable[2]=arrayOf(intArrayOf(0,2),intArrayOf(1,3),intArrayOf(4,5),intArrayOf(6,7))//U
  movesMovable[4]=Array(4){i->intArrayOf(i,i+4)}//[[0,4],[1,5],[2,6],[3,7]] (B)
  //Easy way to get move specifications for R/D/F
  for(i in 0..4 step 2) movesMovable[i+1]=Array(4){j->movesMovable[i][j].reversedArray()}
  val movesImmovable=Array<Array<IntArray>>(6){_->arrayOf()}
  val boardParameters=BoardParameters(boardSize,movesMovable,movesImmovable)

  //Initialize with all possible starting boards
  val seeds=SeedSet(boardParameters.boardSize)
  for(i in 0..<boardParameters.boardSize-1){
    val first=1L shl BoardHelper.getShift(i)
    for(j in i+1..<boardParameters.boardSize){
      val second=1L shl BoardHelper.getShift(j)
      //This adds boards with every combination of 2s and 4s at the given indices
      for(a in 0..1) for(b in 0..1) seeds.add((first shl a) xor (second shl b))
    }
  }

  Kernel.listPositions(boardParameters,"2x2x2",seeds,null)

  fun target(brd:Long):Boolean{
    val maxSum=BoardHelper.getMaxSum(boardParameters.boardSize)
    val sum=BoardHelper.getTileSum(brd,boardParameters.boardSize)
    return sum==maxSum
  }
  Kernel.makeTables(boardParameters,Kernel.pairSelf("2x2x2"),null){brd->target(brd)}

  val moveNames=arrayOf("L","R","D","U","B","F")
  val template="0   1   | 4   5   \n2   3   | 6   7   "
  Kernel.playAIGame(boardParameters,moveNames,Kernel.pairSelf("2x2x2"),0x10000001L,template,true)
}
```
