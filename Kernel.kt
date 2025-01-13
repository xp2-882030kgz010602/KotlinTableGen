import tableGenDataStructures.SeedSet
import tableGenDataStructures.TableGenMap
import tableGenDataStructures.TableGenTree
import board.BoardHelper
import board.BoardIO
import board.BoardMovement
import board.StringPair
import java.io.BufferedInputStream
import kotlin.time.TimeSource

//import kotlin.random.Random


/** This is the kernel of the program. This is where position listing and table generation happen.
 * Note that boards in a board list are ALWAYS sorted in ascending order with respect to their board representations.
 * Tables are broken up into separate files. Boards with the same sum and data for those boards are put into their own "pages". */
class Kernel{
  companion object{
    //The largest possible denominator is 130. Naively, one might expect it to be 140, since the largest boards
    //that this program can handle have 14 tiles, but for 140 to be possible, you'd need to spawn a tile
    //in a blank board, which is impossible since no moves are possible on a blank board.
    //Then observe that 7*10^16 < (2^63-1)/130 < 2^56-1, and that the three values are pretty close together.
    //I've chosen 7*10^16 here because it ends with a lot of zeros, which is useful since 2s and 4s spawn with
    //9/10 and 1/10 probabilities respectively. Finally, fitting just within 2^56 means that 7 bytes are just enough,
    //allowing for pretty efficient storage.
    private const val PROBABILITY_ONE =70000_0000_0000_0000L
    /** `pairSelf(string)` is `(string,string)`. */
    fun pairSelf(string:String) = Pair(string,string)
    /** Given a set of board parameters, a set of "seed boards", and optionally a path pointing to a directory containing a preexisting board list,
     * generates a list of boards reachable from at least one seed board via at least one sequence of valid moves and spawns.
     * These boards will always be written in ascending order.
     * If `preexistingListPath` is given, the output board list includes all boards in the preexisting board list. */
    fun listPositions(boardParameters:BoardParameters,path:String,seedBoards:SeedSet,preexistingListPath:String?){
      println("Beginning board listing.")
      val boardSize=boardParameters.boardSize
      val movesMovable=boardParameters.movesMovable
      val movesImmovable=boardParameters.movesImmovable
      val maxSum=BoardHelper.getMaxSum(boardSize)
      val boardByteSize=BoardIO.getByteSize(boardSize)
      var plusZero=TableGenTree(boardSize)
      var plusTwo =TableGenTree(boardSize)
      var plusFour=TableGenTree(boardSize)
      //If we have preexisting boards, let's write them and filter them out
      for(sum in 0..maxSum step 2){
        //Add seed boards from this sum
        val seeds=seedBoards.get(sum)
        if(seeds!=null) plusZero.merge(seeds)
        val output=BoardIO.getWriter(path,sum,"-list")
        if(preexistingListPath!=null){
          val input=BoardIO.getReader(preexistingListPath,sum,"-list")
          val reader={stream:BufferedInputStream->run{
            val data=BoardIO.readData(stream,boardByteSize)
            if(data==-1L) Long.MAX_VALUE else data//Observe that `Long.MAX_VALUE` is greater than any possible board
          }}
          val plusZeroFiltered=TableGenTree(boardSize)
          var inputBoard=reader(input)
          plusZero.iterate(0){board->
            //Having boards written in ascending order means we can simply stream boards like this
            //to see if we already have any of our boards in our preexisting data
            //First, look for a board that is >= our current board, and output boards we find along the way
            //We don't have to worry about accidentally outputting `Long.MAX_VALUE`, because
            //`Long.MAX_VALUE>board`
            while(inputBoard<board){
              BoardIO.writeData(output,inputBoard,boardByteSize)
              inputBoard=reader(input)
            }
            //We know that this board is not in our preexisting data since we "skipped" over it,
            //so let's add it and output it
            if(inputBoard>board){
              BoardIO.writeData(output,board,boardByteSize)
              plusZeroFiltered.add(board)
            }
            //Otherwise `inputBoard==board` and we already have this board, so we don't need to add it to our tree again
            //We also don't need to output it either, since it will get outputted "as inputBoard"
          }
          //Finally, output any remaining boards (boards that are greater than all boards in `plusZero`)
          while(inputBoard<Long.MAX_VALUE){
            BoardIO.writeData(output,inputBoard,boardByteSize)
            inputBoard=reader(input)
          }
          input.close()
          plusZero=plusZeroFiltered
        }else plusZero.iterate(0L){brd->BoardIO.writeData(output,brd,boardByteSize)}
        output.close()
        //Count boards
        var numBoards=0
        plusZero.iterate(0){numBoards+=1}
        println("Sum=$sum; Number of boards=$numBoards")
        //List out all possible boards that can appear by making a move from boards in `plusZero`
        //We don't need to do move logic for the last two sums,
        //since a move+4 spawn from any such board would yield a board with sum greater than maxSum
        val time=TimeSource.Monotonic
        val timeStart=time.markNow()
        if(maxSum-sum>=4) plusZero.iterate(0L){board->
          //Try each move
          BoardMovement.iterateMoves(board,movesMovable,movesImmovable)
          {_,boardMoved->BoardMovement.iterateSpawns(boardMoved,boardSize,{brd->plusTwo.add(brd)},{brd->plusFour.add(brd)})}
        }
        val timeEnd=time.markNow()
        println("Time: ${timeEnd-timeStart}")
        //Shuffle these around for the next loop
        plusZero=plusTwo
        plusTwo =plusFour
        plusFour=TableGenTree(boardSize)
      }
    }
    /** Generates a table with probabilities and move indices for a given board list. Attempts to maximize the chance of
     * reaching a board that satisfies the target function. |
     * `boardParameters`: The specification for this board. |
     * `paths`: A pair of paths. `paths.first` points to the directory where boards are stored,
     * and `paths.second` points to the directory where probability and move index data will be stored. |
     * `preexistingTablePaths`: If provided, acts like `paths` for a preexisting table. The kernel will use entries
     * from this preexisting table instead of generating new ones whenever possible. |
     * `target`: A function where `target(board)` is true on boards with the desired property, and false otherwise. |
     * Precondition: If `preexistingTablePath` is given, all boards in the specified table
     * must be in the board list that we are using to generate a table. */
    fun makeTables(boardParameters:BoardParameters,paths:StringPair,preexistingTablePaths:StringPair?,target:(Long)->Boolean){
      println("Beginning table generation.")
      val boardSize=boardParameters.boardSize
      val maxSum=BoardHelper.getMaxSum(boardSize)
      val boardByteSize=BoardIO.getByteSize(boardSize)
      //These are blank for now; they will get filled in later
      //`plusZeroIndices[i]` has the move index for `plusZeroBoards[i]`, and `plusZeroProbabilities` has the probability
      //This applies to the `plusTwo` and `plusFour` arrays too
      var plusZeroIndices:IntArray
      var plusZeroData:TableGenMap
      var plusTwoData =TableGenMap(LongArray(0))
      var plusFourData=TableGenMap(LongArray(0))
      for(sum in maxSum downTo 0 step 2){
        val listInputFile=BoardIO.getFile(paths.first,sum,"-list")
        val plusZeroBoards=BoardIO.readBoardList(listInputFile,boardSize)
        plusZeroData=TableGenMap(plusZeroBoards)
        plusZeroIndices=IntArray(plusZeroBoards.size){_->-1}
        if(preexistingTablePaths!=null){
          var i=0
          BoardIO.iteratePageEntries(preexistingTablePaths,sum,boardByteSize){board,probability,index->
            while(plusZeroData.boards[i]<board) i+=1//Once again using the fact that boards are sorted in ascending order
            plusZeroData.probabilities[i]=probability
            plusZeroIndices[i]=index
          }
        }
        println("Generating page for sum=$sum; Number of boards=${plusZeroData.boards.size}")
        val time=TimeSource.Monotonic
        val timeStart=time.markNow()
        plusZeroData.boards.onEachIndexed{i,board->
          if(plusZeroIndices[i]==-1){//Don't need to anything on entries that come from a preexisting table
            plusZeroIndices[i]=255//One byte per index; I reserve 255 for "no move possible or needed"
            if(target(board)) plusZeroData.probabilities[i]=PROBABILITY_ONE//No move needed
            else if(maxSum-sum>=4){//Main probability calculation loop
              BoardMovement.iterateMoves(board,boardParameters.movesMovable,boardParameters.movesImmovable){moveIndex,boardMoved->//Try each move
                var twoProbability=0L
                var fourProbability=0L
                //Division by 10 allows us to do probability calculations with Longs
                val denominator=10L*BoardHelper.numBlankSpaces(boardMoved,boardSize)
                BoardMovement.iterateSpawns(boardMoved,boardSize,
                  {brd->twoProbability+=plusTwoData.get(brd)},
                  {brd->fourProbability+=plusFourData.get(brd)}
                )
                val moveProbability=(fourProbability + 9L*twoProbability)/denominator
                if(moveProbability>=plusZeroData.probabilities[i]){
                  plusZeroData.probabilities[i]=moveProbability
                  plusZeroIndices[i]=moveIndex
                }
              }
            }
          }
        }
        val timeEnd=time.markNow()
        println("Time: ${timeEnd-timeStart}")
        val indexOutput=BoardIO.getWriter(paths.second,sum,"-index")
        val probabilityOutput=BoardIO.getWriter(paths.second,sum,"-chance")
        plusZeroData.boards.onEachIndexed{i,_->
          indexOutput.write(plusZeroIndices[i])
          BoardIO.writeData(probabilityOutput,plusZeroData.probabilities[i],7)
        }
        indexOutput.close()
        probabilityOutput.close()
        //Shuffle these around for the next loop
        plusFourData=plusTwoData
        plusTwoData =plusZeroData
      }
    }
    /** Displays the move probabilities for a given board. Note that this streams probability data several times per move.
     * I made this choice so that this part could run on computers with more limited RAM, even if table generation would still be impractical. */
    fun displayMoveProbabilities(boardParameters:BoardParameters,moveNames:Array<String>,paths:StringPair,board:Long){
      val boardSize=boardParameters.boardSize
      val sum=BoardHelper.getTileSum(board,boardSize)
      val boardByteSize=BoardIO.getByteSize(boardSize)
      BoardMovement.iterateMoves(board,boardParameters.movesMovable,boardParameters.movesImmovable){moveIndex,boardMoved->
        val numBlankSpaces=BoardHelper.numBlankSpaces(boardMoved,boardSize)
        val plusTwoData=TableGenMap(LongArray(numBlankSpaces))
        val plusFourData=TableGenMap(LongArray(numBlankSpaces))
        var j=0
        BoardMovement.iterateSpawns(boardMoved,boardSize,
          {brd->plusTwoData.boards[j]=brd},
          {
            brd->plusFourData.boards[j]=brd
            j+=1
          }
        )
        //Here we load in ONLY the probabilities for the specific boards that could result from a spawn, so that summation works
        BoardIO.fillMap(paths,sum+2,boardByteSize,plusTwoData)
        BoardIO.fillMap(paths,sum+4,boardByteSize,plusFourData)
        val twoProbability=plusTwoData.probabilities.sum()
        val fourProbability=plusFourData.probabilities.sum()
        val denominator=10L*numBlankSpaces
        val moveProbability=(fourProbability + 9L*twoProbability)/denominator
        val probabilityString=BoardIO.formatProbability(moveProbability)
        println("Chance: $probabilityString/$PROBABILITY_ONE for move ${moveNames[moveIndex]}")
      }
    }
    /** Plays an AI game with the given inputs. |
     * `boardParameters`: The specification for this board. |
     * `moveNames`: The names of the moves for this board. `moveNames[i]` is the name of the move specified by
     * `boardParameters.movesMovable[i]` and `boardParameters.movesImmovable[i]`. |
     * `paths`: The paths to the table files. `paths.first` points to the directory where boards are stored,
     * and `paths.second` points to the directory where probability and move index data is stored. |
     * `initialBoard`: The board that the AI starts at. |
     * `template`: A formatting template for boards. |
     * `showProbabilities`: A parameter specifying whether to print probabilities for all possible moves. */
    fun playAIGame(boardParameters:BoardParameters,moveNames:Array<String>,paths:StringPair,initialBoard:Long,template:String,showProbabilities:Boolean){
      val boardSize=boardParameters.boardSize
      val movesMovable=boardParameters.movesMovable
      val movesImmovable=boardParameters.movesImmovable
      val boardByteSize=BoardIO.getByteSize(boardSize)
      var board=initialBoard
      var sum=BoardHelper.getTileSum(board,boardSize)
      var spawnIndex:Int?=null
      while(true){
        println(BoardIO.formatBoard(board,template,spawnIndex))
        val (probability,index)=BoardIO.readSingleEntry(paths,sum,boardByteSize,board)
        val probabilityString=BoardIO.formatProbability(probability)
        println("Chance: $probabilityString/$PROBABILITY_ONE")
        if(index==255){//No more moves possible/needed
          println("No more moves " + (if(probability==0L) "possible" else "needed"))
          return
        }
        println("Optimal move: ${moveNames[index]}")
        if(showProbabilities) displayMoveProbabilities(boardParameters,moveNames,paths,board)
        println("")
        //Make the move
        board=BoardMovement.moveBoard(board,movesMovable[index],movesImmovable[index])
        //Spawn a tile and related stuff
        val indices=IntArray(BoardHelper.numBlankSpaces(board,boardSize))
        //Grab indices of blank tiles
        var i=0
        BoardHelper.iterateTiles(board,boardSize){j,tile->
          if(tile==0L){
            indices[i]=j
            i+=1
          }
        }
        val spawns=LongArray(10){_->2L}
        spawns[0]=4L//10% 4s
        indices.shuffle()//Choose an index at random (or pseudorandom)
        spawnIndex=indices[0]//Update for printing on the next loop
        spawns.shuffle()//Like before; choose a spawn
        sum+=spawns[0].toInt()
        val spawnTile=spawns[0] shr 1//4 is represented as 2 and 2 is represented as 1
        board=board xor (spawnTile shl BoardHelper.getShift(spawnIndex))
      }
    }
    /** `makeRectangleBoardSpecification(width,height)` is a board specification for a rectangular board of the given dimensions.
     * The matching `moveNames` array is `["L","R","D","U"]`. Indexing is done in reading order.
     * Precondition: `width>=2` and `height>=2`. */
    fun makeRectangleBoardSpecification(width:Int,height:Int):BoardParameters{
      //List out "left" and "up" columns
      val left=Array(height){_->IntArray(width)}
      for(row in 0..<height) for(column in 0..<width) left[row][column]=row*width + column
      val up=Array(width){_->IntArray(height)}
      for(column in 0..<width) for(row in 0..<height) up[column][row]=column + width*row
      //List out "right" and "down" columns by simply reversing each column from "left" and "up"
      val right=Array(height){i->left[i].reversedArray()}
      val down=Array(width){i->up[i].reversedArray()}
      return BoardParameters(width*height,arrayOf(left,right,down,up),Array(4){_->emptyArray()})
    }
    /** `makeRectangleBoardTemplate(width,height)` is a formatting template for a rectangular board of the given dimensions.
     * Indexing is done in reading order.
     * Precondition: `width>=2` and `height>=2`. */
    fun makeRectangleBoardTemplate(width:Int,height:Int):String{
      val rows=Array(height){_->""}
      for(row in 0..<height){
        val offset=row*width
        for(column in 0..<width) rows[row]+=(offset+column).toString(16)+"   "
      }
      return rows.joinToString("\n")
    }
  }
}