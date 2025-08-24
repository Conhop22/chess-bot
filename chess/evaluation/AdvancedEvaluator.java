package chess.evaluation;

import java.util.ArrayList;
import java.util.List;

import chess.board.ArrayBoard;
import chess.board.ArrayPiece;
import chess.util.Iteratorable;

/*
 * Evaluator that uses
 * 
 * Material weights
 * Positioning weights
 * Bonuses/penalties for:
 * 2 bishops, 2 knights, 2 rooks, no pawns, tempo
 * Number of pawns effects knights/rooks/dual bishops
 * 
 * Uses a tapered evaluation function: http://chessprogramming.wikispaces.com/Tapered+Eval
 * 
 */
public class AdvancedEvaluator implements Evaluator<ArrayBoard> {

	// Player color
	private static final int WHITE = ArrayBoard.WHITE;
	private static final int BLACK = ArrayBoard.BLACK;

	private static final int STALEMATE = 0;
	private static final int MATE      = 300000;	
	private static final int INFINITY  = 1000000;

	private static final int PHASE_CONSTANT = 256;

	// Material value of a piece 
	private static final int pawnValue   = 100;
	private static final int knightValue = 320;
	private static final int bishopValue = 330;
	private static final int rookValue   = 500;
	private static final int queenValue  = 900;
	private static final int kingValue   = MATE;

	// Penalties for having 2 knights, rooks, or no pawns
	private static final int knightPenalty = -10;
	private static final int rookPenalty = -20;
	private static final int noPawnsPenalty = -20;

	// Bonuses depending on how many pawns are left
	private static final int[] knightPawnAdjustment =
		{-30, -20, -15, -10, -5, 0, 5, 10, 15}; 

	private static final int[] rookPawnAdjustment =
		{25, 20, 15, 10, 5, 0, -5, -10, -15}; 

	private static final int[] dualBishopPawnAdjustment =
		{40, 40, 35, 30, 25, 20, 20, 15, 15};

	private static final int tempoBonus = 10;

	/*
	 * Piece value tables modify the value of each piece according to where it
	 * is on the board.
	 * 
	 * To orient these tables, each row of 8 represents one row (rank) of the
	 * chess board.
	 * 
	 * !!! The first row is where white's pieces start !!!
	 * 
	 * So, for example
	 * having a pawn at d2 is worth -20 for white. Having it at d7 is worth
	 * 50. Note that these have to be flipped over to evaluate black's pawns
	 * since pawn values are not symmetric.
	 * 
	 * Values based on:
	 * http://chessprogramming.wikispaces.com/Simplified+evaluation+function
	 * http://chessprogramming.wikispaces.com/CPW-Engine_eval
	 */

	private static final int pawnPosWhite[][] =
		{
		{0,   0,   0,   0,   0,   0,  0,  0},
		{5,  10,  10, -20, -20,  10, 10,  5},
		{5,  -5, -10,   0,   0, -10, -5,  5},
		{0,   0,   0,  20,  20,   0,  0,  0},
		{5,   5,  10,  25,  25,  10,  5,  5},		
		{10, 10,  20,  30,  30,  20, 10, 10},
		{50, 50,  50,  50,  50,  50, 50, 50},
		{0,   0,   0,   0,   0,   0,  0,  0}
		};

	private static final int pawnPosBlack[][] =
		{
		{0,   0,   0,   0,   0,   0,  0,  0},
		{50, 50,  50,  50,  50,  50, 50, 50},
		{10, 10,  20,  30,  30,  20, 10, 10},
		{5,   5,  10,  25,  25,  10,  5,  5},		
		{0,   0,   0,  20,  20,   0,  0,  0},
		{5,  -5, -10,   0,   0, -10, -5,  5},
		{5,  10,  10, -20, -20,  10, 10,  5},
		{0,   0,   0,   0,   0,   0,  0,  0}
		};

	private static final int knightPosWhite[][] =
		{
		{-50, -40, -30, -30, -30, -30, -40, -50},
		{-40, -20,   0,   5,   5,   0, -20, -40},
		{-30,   5,  10,  15,  15,  10,   5, -30},
		{-30,   0,  15,  20,  20,  15,   0, -30},
		{-30,   5,  15,  20,  20,  15,   5, -30},	 
		{-30,   0,  10,  15,  15,  10,   0, -30},	 
		{-40, -20,   0,   0,   0,   0, -20, -40},
		{-50, -40, -30, -30, -30, -30, -40, -50}
		};

	private static final int knightPosBlack[][] =
		{
		{-50, -40, -30, -30, -30, -30, -40, -50},
		{-40, -20,   0,   0,   0,   0, -20, -40},
		{-30,   0,  10,  15,  15,  10,   0, -30},	 
		{-30,   5,  15,  20,  20,  15,   5, -30},
		{-30,   0,  15,  20,  20,  15,   0, -30},
		{-30,   5,  10,  15,  15,  10,   5, -30},
		{-40, -20,   0,   5,   5,   0, -20, -40},
		{-50, -40, -30, -30, -30, -30, -40, -50}
		};

	private static final int bishopPosWhite[][] =
		{
		{-20, -10, -10, -10, -10, -10, -10, -20},
		{-10,   5,   0,   0,   0,   0,   5, -10},
		{-10,  10,  10,  10,  10,  10,  10, -10},     
		{-10,   0,  10,  10,  10,  10,   0, -10},     
		{-10,   5,   5,  10,  10,   5,   5, -10},     
		{-10,   0,   5,  10,  10,   5,   0, -10},     
		{-10,   0,   0,   0,   0,   0,   0, -10},     
		{-20, -10, -10, -10, -10, -10, -10, -20}
		};

	private static final int bishopPosBlack[][] =
		{
		{-20, -10, -10, -10, -10, -10, -10, -20},
		{-10,   0,   0,   0,   0,   0,   0, -10},
		{-10,   0,   5,  10,  10,   5,   0, -10},
		{-10,   5,   5,  10,  10,   5,   5, -10},     
		{-10,   0,  10,  10,  10,  10,   0, -10},     
		{-10,  10,  10,  10,  10,  10,  10, -10},     
		{-10,   5,   0,   0,   0,   0,   5, -10},
		{-20, -10, -10, -10, -10, -10, -10, -20}
		};

	private static final int rookPosWhite[][] =
		{
		{0,  0,  0,  5,  5,  0,  0,  0},
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{5, 10, 10, 10, 10, 10, 10,  5},
		{0,  5,  5,  5,  5,  5,  5,  0}
		};

	private static final int rookPosBlack[][] =
		{
		{0,  5,  5,  5,  5,  5,  5,  0},
		{5, 10, 10, 10, 10, 10, 10,  5},
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{-5, 0,  0,  0,  0,  0,  0, -5},	 
		{0,  0,  0,  5,  5,  0,  0,  0}
		};

	private static final int queenPosWhite[][] =
		{
		{-20, -10, -10, -5, -5, -10, -10, -20},
		{-10,   0,   5,  0,  0,   0,   0, -10},	 
		{-10,   5,   5,  5,  5,   5,   0, -10},	 
		{0,     0,   5,  5,  5,   5,   0, -5},	 
		{-5,    0,   5,  5,  5,   5,   0, -5},	 
		{-10,   0,   5,  5,  5,   5,   0, -10},	 		
		{-10,   0,   0,  0,  0,   0,   0, -10},
		{-20, -10, -10, -5, -5, -10, -10, -20}
		};

	private static final int queenPosBlack[][] =
		{
		{-20, -10, -10, -5, -5, -10, -10, -20},
		{-10,   0,   0,  0,  0,   0,   0, -10},
		{-10,   0,   5,  5,  5,   5,   0, -10},	 
		{-5,    0,   5,  5,  5,   5,   0, -5},	 
		{0,     0,   5,  5,  5,   5,   0, -5},	 
		{-10,   5,   5,  5,  5,   5,   0, -10},	 
		{-10,   0,   5,  0,  0,   0,   0, -10},	 
		{-20, -10, -10, -5, -5, -10, -10, -20}
		};

	private static final int kingPosWhite[][] =
		{
		{20,   30,  10,   0,   0,  10,  30,  20},
		{20,   20,   0,   0,   0,   0,  20,  20},	 
		{-10, -20, -20, -20, -20, -20, -20, -10},	 		
		{-20, -30, -30, -40, -40, -30, -30, -20},	 
		{-30, -40, -40, -50, -50, -40, -40, -30},		
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-30, -40, -40, -50, -50, -40, -40, -30}
		};

	private static final int kingPosBlack[][] =
		{
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-30, -40, -40, -50, -50, -40, -40, -30},
		{-20, -30, -30, -40, -40, -30, -30, -20},	 
		{-10, -20, -20, -20, -20, -20, -20, -10},	 
		{20,   20,   0,   0,   0,   0,  20,  20},	 
		{20,   30,  10,   0,   0,  10,  30,  20}
		};

	private static final int kingPosWhiteEnd[][] =
		{
		{-50, -30, -30, -30, -30, -30, -30, -50},
		{-30, -30,   0,   0,   0,   0, -30, -30},	 
		{-30, -10,  20,  30,  30,  20, -10, -30},					
		{-30, -10,  30,  40,  40,  30, -10, -30},	 		
		{-30, -10,  30,  40,  40,  30, -10, -30},				
		{-30, -10,  20,  30,  30,  20, -10, -30},		
		{-30, -20, -10,   0,   0, -10, -20, -30},
		{-50, -40, -30, -20, -20, -30, -40, -50}
		};

	private static final int kingPosBlackEnd[][] =
		{
		{-50, -40, -30, -20, -20, -30, -40, -50},
		{-30, -20, -10,   0,   0, -10, -20, -30},
		{-30, -10,  20,  30,  30,  20, -10, -30},		
		{-30, -10,  30,  40,  40,  30, -10, -30},		
		{-30, -10,  30,  40,  40,  30, -10, -30},	 		
		{-30, -10,  20,  30,  30,  20, -10, -30},			
		{-30, -30,   0,   0,   0,   0, -30, -30},	 
		{-50, -30, -30, -30, -30, -30, -30, -50}
		};

	/*
	 * This is the evaluator. It simply returns a score for the board position
	 * with respect to the player to move.
	 * 
	 * The evaluation function gives a score for each piece according to the
	 * pieceValue array below, and an additional amount for each piece depending
	 * on where it is (see comment below).
	 * 
	 * The eval of a position is the value of the pieces of the player whose
	 * turn it is, minus the value of the pieces of the other player (plus the
	 * castling points thrown in).
	 * 
	 * If it's WHITE's turn, and white is up a queen, then the value will be
	 * roughly 900. If it's BLACK's turn and white is up a queen, then the value
	 * returned should be about -900.
	 */
	public int eval(ArrayBoard board) {

		// Get current player
		int player = board.toPlay();
		int opponent = (player == WHITE) ? BLACK : WHITE;		

		int openingPlayerValue = getValueOfPieces(board, player, false);
		int endPlayerValue = getValueOfPieces(board, player, true);

		int openingOpponentValue = getValueOfPieces(board, opponent, false);
		int endOpponentValue = getValueOfPieces(board, opponent, true);

		// Weigh the two evaluation functions based on the current phase
		int phase = currentPhase(board);
		int playerValue = ((openingPlayerValue * (PHASE_CONSTANT - phase)) +
				(endPlayerValue * phase)) / PHASE_CONSTANT;
		int opponentValue = ((openingOpponentValue * (PHASE_CONSTANT - phase)) +
				(endOpponentValue * phase)) / PHASE_CONSTANT;

		// Return the difference between our current score and opponents
		int playerMobility = calculateMobility(board, player);
		int opponentMobility = calculateMobility(board, opponent);

		int mobilityScore = (playerMobility - opponentMobility) * 2;
		if (playerValue < MATE - 1000){
			return (playerValue + mobilityScore) - opponentValue - 3000000;
			
		}else{
			return (playerValue + mobilityScore) - opponentValue;

		}
		
	}	

	private ArrayPiece getPinnedPiece(ArrayBoard board, ArrayPiece attacker, ArrayPiece[][] pieceMap) {
    int attackerType = attacker.type();
    if (attackerType != ArrayPiece.BISHOP && attackerType != ArrayPiece.ROOK && attackerType != ArrayPiece.QUEEN) {
        return null;
    }

    int[][] directions;
    if (attackerType == ArrayPiece.BISHOP) {
        directions = new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    } else if (attackerType == ArrayPiece.ROOK) {
        directions = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    } else {
        directions = new int[][]{
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };
    }

    int attackerColor = attacker.color();
    int opponentColor = (attackerColor == WHITE) ? BLACK : WHITE;

    for (int[] dir : directions) {
        int row = attacker.row();
        int col = attacker.col();
        boolean foundBlocker = false;
        ArrayPiece blocker = null;

        while (true) {
            row += dir[0];
            col += dir[1];

            if (row < 0 || row >= 8 || col < 0 || col >= 8) break;

            ArrayPiece piece = pieceMap[row][col];
            if (piece == null) continue;

            if (piece.color() == attackerColor) break; // Friendly piece blocks

            if (!foundBlocker) {
                blocker = piece;
                foundBlocker = true;
            } else {
                // Second piece along the ray
                if (piece.color() == opponentColor && piece.type() == ArrayPiece.KING) {
                    return blocker; // The blocker is pinned to the king
                } else {
                    break;
                }
            }
        }
    }

    return null;
}
	private int getBaseValue(int pieceType) {
    switch (pieceType) {
        case ArrayPiece.PAWN:   return pawnValue;
        case ArrayPiece.KNIGHT: return knightValue;
        case ArrayPiece.BISHOP: return bishopValue;
        case ArrayPiece.ROOK:   return rookValue;
        case ArrayPiece.QUEEN:  return queenValue;
        case ArrayPiece.KING:   return kingValue;
        default: return 0;
    }
}
	private int calculateMobility(ArrayBoard board, int player) {
    int mobility = 0;
    Iteratorable<ArrayPiece> pieces = board.allPiecesOfColor(player);

    for (ArrayPiece piece : pieces) {
        mobility += getAttackedSquares(board, piece).size();
    }

    return mobility;
}
	private ArrayPiece pieceAt(ArrayBoard board, int row, int col) {
    for (ArrayPiece p : board.allPieces()) {
        if (p.row() == row && p.col() == col) {
            return p;
        }
    }
    return null;
}
	private boolean isOnBoard(int row, int col) {
    return row >= 0 && row < 8 && col >= 0 && col < 8;
}
	private List<int[]> getAttackedSquares(ArrayBoard board, ArrayPiece piece) {
    List<int[]> squares = new ArrayList<>();
    int row = piece.row();
    int col = piece.col();
    int color = piece.color();
    int opponentColor = (color == WHITE) ? BLACK : WHITE;

    switch (piece.type()) {
        case ArrayPiece.PAWN:
            int direction = (color == WHITE) ? -1 : 1;
            int[][] pawnAttacks = {{direction, -1}, {direction, 1}};
            for (int[] d : pawnAttacks) {
                int r = row + d[0];
                int c = col + d[1];
                if (isOnBoard(r, c)) {
                    squares.add(new int[]{r, c});
                }
            }
            break;

        case ArrayPiece.KNIGHT:
            int[][] knightMoves = {
                {-2, -1}, {-1, -2}, {1, -2}, {2, -1},
                {2, 1}, {1, 2}, {-1, 2}, {-2, 1}
            };
            for (int[] d : knightMoves) {
                int r = row + d[0];
                int c = col + d[1];
                if (isOnBoard(r, c)) {
                    squares.add(new int[]{r, c});
                }
            }
            break;

        case ArrayPiece.BISHOP:
            addSlidingAttacks(squares, board, row, col, color, new int[][] {
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
            });
            break;

        case ArrayPiece.ROOK:
            addSlidingAttacks(squares, board, row, col, color, new int[][] {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
            });
            break;

        case ArrayPiece.QUEEN:
            addSlidingAttacks(squares, board, row, col, color, new int[][] {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
            });
            break;

        case ArrayPiece.KING:
            int[][] kingMoves = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1},          {0, 1},
                {1, -1},  {1, 0}, {1, 1}
            };
            for (int[] d : kingMoves) {
                int r = row + d[0];
                int c = col + d[1];
                if (isOnBoard(r, c)) {
                    squares.add(new int[]{r, c});
                }
            }
            break;
    }

    return squares;
}
	private void addSlidingAttacks(List<int[]> squares, ArrayBoard board, int row, int col, int color, int[][] directions) {
    for (int[] dir : directions) {
        int r = row + dir[0];
        int c = col + dir[1];

        while (isOnBoard(r, c)) {
            squares.add(new int[]{r, c});  // We "see" this square.

            ArrayPiece target = pieceAt(board, r, c);
            if (target != null) {
                break; // Stop if a piece blocks further vision.
            }

            r += dir[0];
            c += dir[1];
        }
    }
}


	private int getValueOfPieces(ArrayBoard board, int player, boolean isEndGame) {

		Iteratorable<ArrayPiece> pieces = board.allPiecesOfColor(player);

		// Determine which arrays to use
		int[][] pawnPos, knightPos, bishopPos, rookPos, queenPos, kingPos;
		int pawnCount, opponentPawnCount, bishopCount,
		knightCount, rookCount, queenCount;
		if (player == WHITE) {
			pawnPos = pawnPosWhite;
			knightPos = knightPosWhite;
			bishopPos = bishopPosWhite;
			rookPos = rookPosWhite;
			queenPos = queenPosWhite;
			if (isEndGame) {
				kingPos = kingPosWhiteEnd;
			}
			else {			
				kingPos = kingPosWhite;
			}			
			pawnCount = board.countOfPiece(ArrayPiece.WHITE_PAWN);
			opponentPawnCount = board.countOfPiece(ArrayPiece.BLACK_PAWN);
			bishopCount = board.countOfPiece(ArrayPiece.WHITE_BISHOP);
			knightCount = board.countOfPiece(ArrayPiece.WHITE_KNIGHT);
			rookCount = board.countOfPiece(ArrayPiece.WHITE_ROOK);
			queenCount = board.countOfPiece(ArrayPiece.WHITE_QUEEN);
		}
		else {
			pawnPos = pawnPosBlack;
			knightPos = knightPosBlack;
			bishopPos = bishopPosBlack;
			rookPos = rookPosBlack;
			queenPos = queenPosBlack;
			if (isEndGame) {
				kingPos = kingPosBlackEnd;
			}
			else {			
				kingPos = kingPosBlack;
			}
			pawnCount = board.countOfPiece(ArrayPiece.BLACK_PAWN);	
			opponentPawnCount = board.countOfPiece(ArrayPiece.WHITE_PAWN);			
			bishopCount = board.countOfPiece(ArrayPiece.BLACK_BISHOP);
			knightCount = board.countOfPiece(ArrayPiece.BLACK_KNIGHT);
			rookCount = board.countOfPiece(ArrayPiece.BLACK_ROOK);	
			queenCount = board.countOfPiece(ArrayPiece.BLACK_QUEEN);			
		}

		int value = 0;		
		ArrayPiece[][] pieceMap = new ArrayPiece[8][8];
for (ArrayPiece p : board.allPieces()) {
    pieceMap[p.row()][p.col()] = p;
}
		for(ArrayPiece piece : pieces) {
			switch(piece.type()) {
			case ArrayPiece.PAWN:
				value += pawnValue + pawnPos[piece.row()][piece.col()];
				break;				
			case ArrayPiece.KNIGHT:
				value += knightValue + knightPos[piece.row()][piece.col()] +
				knightPawnAdjustment[pawnCount];
				break;
			case ArrayPiece.BISHOP:
				value += bishopValue + bishopPos[piece.row()][piece.col()];
				break;				
			case ArrayPiece.ROOK:
				value += rookValue + rookPos[piece.row()][piece.col()] +
				rookPawnAdjustment[pawnCount];
				break;
			case ArrayPiece.QUEEN:
				value += queenValue + queenPos[piece.row()][piece.col()];
				break;
			case ArrayPiece.KING:
				value += kingValue + kingPos[piece.row()][piece.col()];
				break;
			}
			ArrayPiece pinned = getPinnedPiece(board, piece, pieceMap);
    if (pinned != null) {
        int pinBonus = getBaseValue(pinned.type());
        value += pinBonus;
    }

	boolean isDefended = false;
boolean isAttacked = false;

for (ArrayPiece p : board.allPieces()) {
    if (p.color() != piece.color()) {   // only opposite color pieces
        List<int[]> attacks = getAttackedSquares(board, p);
        for (int[] sq : attacks) {
            if (sq[0] == piece.row() && sq[1] == piece.col()) {
                isAttacked = true;
                break;
            }
        }
    }
}

if (isAttacked) {
    boolean isDefendeid = false;
    int lowestDefenderValue = Integer.MAX_VALUE;

    for (ArrayPiece defender : board.allPieces()) {
        if (defender.color() == piece.color()) {
            for (int[] defSq : getAttackedSquares(board, defender)) {
                if (defSq[0] == piece.row() && defSq[1] == piece.col()) {
                    isDefendeid = true;
                    int defenderValue = getBaseValue(defender.type());
                    if (defenderValue < lowestDefenderValue) {
                        lowestDefenderValue = defenderValue;
                    }
                }
            }
        }
    }
    if (!isDefendeid) {
        // Strong penalty if attacked and undefended
        value -= getBaseValue(piece.type()) * 500;
    } else {
        // Moderate penalty if defended but attacked by cheaper pieces
        int lowestAttackerValue = Integer.MAX_VALUE;
        for (ArrayPiece attacker : board.allPieces()) {
            if (attacker.color() != piece.color()) {
                for (int[] sq : getAttackedSquares(board, attacker)) {
                    if (sq[0] == piece.row() && sq[1] == piece.col()) {
                        int attackerVal = getBaseValue(attacker.type());
                        if (attackerVal < lowestAttackerValue) {
                            lowestAttackerValue = attackerVal;
                        }
                    }
                }
            }
        }

        if (lowestAttackerValue < getBaseValue(piece.type())) {
            value -= (getBaseValue(piece.type()) - lowestAttackerValue) * 200;
        }
    }
}

if (isAttacked) {
    int lowestAttackerValue = Integer.MAX_VALUE;

    for (ArrayPiece p : board.allPieces()) {
        if (p.color() != piece.color()) {
            for (int[] sq : getAttackedSquares(board, p)) {
                if (sq[0] == piece.row() && sq[1] == piece.col()) {
                    int attackerVal = getBaseValue(p.type());
                    if (attackerVal < lowestAttackerValue) {
                        lowestAttackerValue = attackerVal;
                    }
                }
            }
        }
    }

    // If an attacker is cheaper than the piece being attacked, penalize it
    if (lowestAttackerValue < getBaseValue(piece.type())) {
        value -= getBaseValue(piece.type()) - lowestAttackerValue;
    }
}

	 for (int[] square : getAttackedSquares(board, piece)) {
        int targetRow = square[0];
        int targetCol = square[1];

        ArrayPiece target = pieceAt(board, targetRow, targetCol);
        
        // Captures or Attacks
        if (target != null && target.color() != piece.color()) {
            int attackerValue = getBaseValue(piece.type());
            int targetValue = getBaseValue(target.type());

            if (targetValue > 0 && attackerValue < targetValue) {
                value += targetValue * 10; // Attack bonus
            }

            // Add capture bonus
            for (ArrayPiece attacker : board.allPieces()) {
    if (attacker.color() != piece.color()) continue; // only evaluate bot's own pieces

    List<int[]> attacks = getAttackedSquares(board, attacker);

    for (int[] squaref : attacks) {
        ArrayPiece targety = pieceAt(board, squaref[0], squaref[1]);

        if (targety != null && targety.color() != piece.color()) {
            boolean isDefenderd = false;

            // Check if target is defended
            for (ArrayPiece defender : board.allPieces()) {
                if (defender.color() == target.color()) {
                    List<int[]> defSquares = getAttackedSquares(board, defender);
                    for (int[] defSq : defSquares) {
                        if (defSq[0] == squaref[0] && defSq[1] == squaref[1]) {
                            isDefended = true;
                            break;
                        }
                    }
                }
            }

            if (!isDefenderd) {
                // Undefended piece — encourage the bot to capture it
                value += getBaseValue(targety.type()) * 2; // Adjust multiplier as needed
            } else if(getBaseValue(targety.type()) < getBaseValue(piece.type())) {
				value += getBaseValue(piece.type()) - getBaseValue(targety.type()) * 2;
			}
        }
    }
}
        }

        // Check bonus — if attacking the king
        if (target != null && target.type() == ArrayPiece.KING && target.color() != piece.color()) {
            value += 150;
        }
    }
	
		}

		// Give two bishops a bonus depending on pawns
		if (bishopCount > 1) {
			value += dualBishopPawnAdjustment[pawnCount];
		}
		if (knightCount > 1) {
			value += knightPenalty;
		}
		if (rookCount > 1) {
			value += rookPenalty;
		}
		if (pawnCount == 0) {
			value += noPawnsPenalty;
		}

		// Bonus if it's our turn: https://chessprogramming.wikispaces.com/Tempo
		if (player == board.toPlay()) {
			value += tempoBonus;
		}


		/*******************************************************************
		 *  Low material correction - guarding against an illusory material *
		 *  advantage.  Program  will not not  expect to  win  having  only *
		 *  a single minor piece and no pawns.                              *
		 *******************************************************************/
		if ((pawnCount == 0) && (value < bishopValue) && (value > 0)) {
			return 0;
		}

		/*******************************************************************
		 *  Program will not expect to win having only two knights in case  *
		 *  neither  side  has pawns.                                       *
		 *******************************************************************/
		if (value > 0 && pawnCount == 0 && opponentPawnCount == 0 && knightCount == 2 &&
				bishopCount == 0 && rookCount == 0 && queenCount == 0) {
			return 0;
		}

		return value;
	}

	// Calculate what part of the game we're in
	// From http://chessprogramming.wikispaces.com/Tapered+Eval
	private int currentPhase(ArrayBoard board) {		
		int knightPhase = 1;
		int bishopPhase = 1;
		int rookPhase = 2;
		int queenPhase = 4;
		int totalPhase = knightPhase*4 + bishopPhase*4 + rookPhase*4 + queenPhase*2;
		int phase = totalPhase;

		phase -= board.countOfType(ArrayPiece.KNIGHT) * knightPhase;
		phase -= board.countOfType(ArrayPiece.BISHOP) * bishopPhase;
		phase -= board.countOfType(ArrayPiece.ROOK)   * rookPhase;
		phase -= board.countOfType(ArrayPiece.QUEEN)  * queenPhase;
		return (phase * PHASE_CONSTANT + (totalPhase / 2)) / totalPhase;
	}

	// Accessors
	public int infty() {
		return INFINITY;
	}

	public int mate() {
		return MATE;
	}

	public int stalemate() {
		return STALEMATE;
	}

	public int weightOfPawn() {
		return pawnValue;
	}
}