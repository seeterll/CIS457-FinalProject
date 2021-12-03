
import java.util.Scanner;
import java.io.IOException;
import java.awt.BorderLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.Formatter;

import javax.swing.*;

import java.net.Socket;
import java.net.ServerSocket;

public class TicTacToeServer extends JFrame 
{
   private Condition opposingPlayerConnected;
   private Condition opposingPlayerTurn;
   
   private User[] playerArray;
   private String[] gameBoard = new String[9];
   private Lock freezeGame;
   private boolean gameOver;
   private JTextArea textOutput;  
   private int activePlayer; 
   private ExecutorService executeGame;
   private ServerSocket serverSocket;
   
   private final static String[] boardMark = {"X", "O"};
   private final static int X_PLAYER = 0; 
   private final static int O_PLAYER = 1;
   
   public static void main(String[] args) {
      TicTacToeServer newGame = new TicTacToeServer();
      newGame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      newGame.execute();
   } 

   public TicTacToeServer() {
      super("Tic Tac Toe Game Server");
      executeGame = Executors.newFixedThreadPool(2);
      freezeGame = new ReentrantLock(); 
      opposingPlayerConnected = freezeGame.newCondition();
      opposingPlayerTurn = freezeGame.newCondition();      

      for (int i = 0; i < 9; i++) {
         gameBoard[i] = new String("");
      }
      activePlayer = X_PLAYER; 
      playerArray = new User[2];

      try {
         serverSocket = new ServerSocket(6251, 2);
      } 
      catch (IOException ioException) {
         ioException.printStackTrace();
         System.exit(1);
      } 

      textOutput = new JTextArea();
      add(textOutput, BorderLayout.CENTER);
      textOutput.setText("Server is currently awaiting any new connections...\n");
      setSize(300, 300);
      setVisible(true); 
   }

   public boolean locationTaken(int point) {
      if (gameBoard[point].equals(boardMark[X_PLAYER]) || 
         gameBoard [point].equals(boardMark[O_PLAYER])) {
            return true;
      }
      else {
         return false;
      }
   }

   public boolean moveValidation(int point, int currentPlayer) {
      while (currentPlayer != activePlayer) {
         freezeGame.lock();
         try {
            opposingPlayerTurn.await();
         } 
         catch (InterruptedException exception)
         {
            exception.printStackTrace();
         } 
         finally {
            freezeGame.unlock();
         } 
      } 

      if (!locationTaken(point)) {
         gameBoard[point] = boardMark[activePlayer]; 
         activePlayer = (activePlayer + 1) % 2; 
         playerArray[activePlayer].otherPlayerMoved(point);
         freezeGame.lock();
         try {
            opposingPlayerTurn.signal();
         } 
         finally {
            freezeGame.unlock();
         } 
         return true; 
      } 
      else {
         return false;
      }
   }

   public void execute() {
      for (int i = 0; i < playerArray.length; i++) {
         try {
            playerArray[i] = new User(serverSocket.accept(), i);
            executeGame.execute(playerArray[i]);
         } 
         catch (IOException ioException) {
            ioException.printStackTrace();
            System.exit(1);
         } 
      }
      freezeGame.lock();
      try {
         playerArray[X_PLAYER].setFreeze(false);
         opposingPlayerConnected.signal();
      } 
      finally {
         freezeGame.unlock();
      } 
   }

   private void displayMessage(final String message) {
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run() {
               textOutput.append(message);
            } 
         } 
      ); 
   }

   private class User implements Runnable {
      private int activePlayer; 
      private Formatter formatOutputter;
      private boolean setFreeze = true; 
      private String currentMark; 
      private Socket connectionSocket; 
      private Scanner userInput;
      private int xWins;
      private int yWins;

      public User(Socket userSocket, int playerNumber) {
         activePlayer = playerNumber;
         connectionSocket = userSocket; 
         currentMark = boardMark[activePlayer];
         
         try {
            userInput = new Scanner(connectionSocket.getInputStream());
            formatOutputter = new Formatter(connectionSocket.getOutputStream());
         } 
         catch (IOException ioException) {
            ioException.printStackTrace();
            System.exit(1);
         } 
      }
      
      public void otherPlayerMoved(int point) {
         formatOutputter.format("Your opponent has moved!\n");
         formatOutputter.format("%d\n", point);
         formatOutputter.flush();
      }

      public void gameEnds() {
         if (activePlayer == X_PLAYER) {
            formatOutputter.format("Game Over\n");
            activePlayer = O_PLAYER;
            formatOutputter.format("Game Over\n");
            formatOutputter.flush();
         }
         else if (activePlayer == O_PLAYER) {
            formatOutputter.format("Game Over\n");
            activePlayer = X_PLAYER;
            formatOutputter.format("Game Over\n");
            formatOutputter.flush();
         }
         for (int i = 0; i < 9; i++) {
            gameBoard[i] = "";
         }
      }

      public void run() {
         try {
            displayMessage("Player " + currentMark + " has connected to the server!\n");
            formatOutputter.format("%s\n", currentMark);
            formatOutputter.flush();

            if (activePlayer == X_PLAYER) {
               formatOutputter.format("%s\n%s", "Player X has connected to the server!",
                  "Waiting for opposing player to connect to the server...\n");
               formatOutputter.flush();

               freezeGame.lock();

               try {
                  while(setFreeze) {
                     opposingPlayerConnected.await();
                  } 
               }  
               catch (InterruptedException exception) {
                  exception.printStackTrace();
               } 
               finally {
                  freezeGame.unlock();
                  gameOver = false;
               } 
               formatOutputter.format("Opposing player has connected it is now your turn!\n");
               formatOutputter.flush();
            } 
            else {
               formatOutputter.format("Player O has connected to the server!\n");
               formatOutputter.flush();
            } 

            // while loop to run the game
            while (true) {
               int location = 0;
               String currentMark = boardMark[activePlayer];

               if (userInput.hasNext()) {
                  location = userInput.nextInt();
               }

               if (moveValidation(location, activePlayer)) {
                  displayMessage("\nMove has been made at square: " + location);
                  formatOutputter.format("This was a valid move...\n"); 
                  formatOutputter.flush();
               } 
               else {
                  formatOutputter.format("This is an invalid move please try again!\n");
                  formatOutputter.flush();
               }
               // horizontal win conditions
               if ((gameBoard[0].equals(currentMark) && gameBoard[1].equals(currentMark) && gameBoard[2].equals(currentMark))
               || (gameBoard[3].equals(currentMark) && gameBoard[4].equals(currentMark) && gameBoard[5].equals(currentMark))
               || (gameBoard[6].equals(currentMark) && gameBoard[7].equals(currentMark) && gameBoard[8].equals(currentMark))
               // diagonal win conditions
               || (gameBoard[0].equals(currentMark) && gameBoard[4].equals(currentMark) && gameBoard[8].equals(currentMark))
               || (gameBoard[2].equals(currentMark) && gameBoard[4].equals(currentMark) && gameBoard[6].equals(currentMark))
               //vertical win conditions
               || (gameBoard[0].equals(currentMark) && gameBoard[3].equals(currentMark) && gameBoard[6].equals(currentMark))
               || (gameBoard[1].equals(currentMark) && gameBoard[4].equals(currentMark) && gameBoard[7].equals(currentMark))
               || (gameBoard[2].equals(currentMark) && gameBoard[5].equals(currentMark) && gameBoard[8].equals(currentMark)))
               {
                  gameOver = true;
                  displayMessage("\nGame over. Player " + currentMark + " has won the game!");
                  displayMessage("\n-------Scoreboard-------");
                  if (currentMark == "X")
                     xWins += 1;
                  else if (currentMark == "Y")
                     yWins += 1;
                  displayMessage("\nX Wins: " + xWins + ", O Wins: " + yWins);
                  displayMessage("\nRestarting the game...");
                  formatOutputter.format("Player: " + currentMark + " has won the game!!!\n");
                  formatOutputter.format("\n-------Scoreboard-------");
                  formatOutputter.format("\nX Wins: " + xWins + ", O Wins: " + yWins + "\n");
                  gameEnds();
                  formatOutputter.format("\nRestarting the game...");
                  formatOutputter.flush();

               }
            }
         }
         finally {
            try {
               connectionSocket.close();
            } 
            catch (IOException ioException) {
               ioException.printStackTrace();
               System.exit(1);
            } 
         } 
      }

      public void setFreeze(boolean freezeStatus) {
         setFreeze = freezeStatus;
      }
   }
}
