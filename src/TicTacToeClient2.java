import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Formatter;
import java.util.Scanner;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

public class TicTacToeClient2 extends JFrame implements Runnable
{
    private final String X_MARK = "X";
    private final String O_MARK = "O";
    private JPanel panel;
    private JPanel additionalPanel;
    private Formatter formatOutputter;
    private String mainHost;
    private String currentMark;
    private boolean currentTurn;
    private Socket connectionSocket;
    private Scanner userInput;
    private Square currentSquare;
    private Square[][] boardArray;
    private JTextField textField;
    private JTextArea textArea;

    public static void main(String[] args) {
        TicTacToeClient newGame;

        if (args.length == 0) {
            newGame = new TicTacToeClient("127.0.0.1");
        }
        else {
            newGame = new TicTacToeClient(args[0]);
            newGame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
    }

    public void run() {
        currentMark = userInput.nextLine();
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run()
                    {
                        textField.setText("You have the mark: \"" + currentMark + "\"");
                    }
                }
        );

        currentTurn = (currentMark.equals(X_MARK));
        while (true) {
            if (userInput.hasNextLine()) {
                processMessage(userInput.nextLine());
            }
        }
    }

    public TicTacToeClient2(String inputHost) {
        mainHost = inputHost;
        textArea = new JTextArea(4, 30);
        textArea.setEditable(false);
        add(new JScrollPane(textArea), BorderLayout.SOUTH);
        panel = new JPanel();
        panel.setLayout(new GridLayout(3, 3, 0, 0));
        boardArray = new Square[3][3];

        for (int row = 0; row < boardArray.length; row++) {
            for (int column = 0; column < boardArray[row].length; column++) {
                boardArray[row][column] = new Square(" ", row * 3 + column);
                panel.add(boardArray[row][column]);
            }
        }

        textField = new JTextField();
        textField.setEditable(false);
        add(textField, BorderLayout.NORTH);
        additionalPanel = new JPanel();
        additionalPanel.add(panel, BorderLayout.CENTER);
        add(additionalPanel, BorderLayout.CENTER);
        setSize(300, 225);
        setVisible(true);
        startClient();
    }

    private class Square extends JPanel {
        private String squareMark;
        private int squarePoint;

        public Square(String gridMark, int point) {
            squareMark = gridMark;
            squarePoint = point;
            addMouseListener(
                    new MouseAdapter() {
                        public void mouseReleased(MouseEvent e) {
                            setCurrentSquare(Square.this);
                            sendClickedSquare(getSquareLocation());
                        }
                    }
            );
        }

        public void paintComponent(Graphics graphicsComp) {
            super.paintComponent(graphicsComp);

            graphicsComp.drawRect(0, 0, 29, 29);
            graphicsComp.drawString(squareMark, 11, 20);
        }

        public void setMark(String newPoint) {
            squareMark = newPoint;
            repaint();
        }

        public int getSquareLocation() {
            return squarePoint;
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public Dimension getPreferredSize() {
            return new Dimension(30, 30);
        }
    }

    private void processMessage(String message) {
        if (message.equals("This was a valid move...")) {
            displayMessage("Valid move has been placed.\n");
            setMark(currentSquare, currentMark);
        }
        else if (message.equals("This is an invalid move please try again!")) {
            displayMessage(message + "\n");
            currentTurn = true;
        }
        else if (message.equals("Your opponent has moved!")) {
            int location = userInput.nextInt();
            userInput.nextLine();
            int row = location / 3;
            int column = location % 3;

            setMark(boardArray[row][column],
                    (currentMark.equals(X_MARK) ? O_MARK : X_MARK));
            displayMessage("Opponent moved. Your turn.\n");
            currentTurn = true;
        }
        else {
            displayMessage(message + "\n");
        }
    }

    private void displayMessage(final String message) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run()
                    {
                        textArea.append(message);
                    }
                }
        );
    }

    public void setCurrentSquare(Square gridPoint) {
        currentSquare = gridPoint;
    }

    public void sendClickedSquare(int point) {
        if (currentTurn) {
            formatOutputter.format("%d\n", point);
            formatOutputter.flush();
            currentTurn = false;
        }
    }

    private void setMark(final Square point, final String inputMark) {
        SwingUtilities.invokeLater(
                new Runnable()
                {
                    public void run()
                    {
                        point.setMark(inputMark);
                    }
                }
        );
    }

    public void startClient() {
        try {
            connectionSocket = new Socket(InetAddress.getByName(mainHost), 6251);
            userInput = new Scanner(connectionSocket.getInputStream());
            formatOutputter = new Formatter(connectionSocket.getOutputStream());
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
        ExecutorService worker = Executors.newFixedThreadPool(1);
        worker.execute(this);
    }
}

