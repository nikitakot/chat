package client;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.io.input.ReversedLinesFileReader;
import sample.Const;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kotn0 on 17.11.2016.
 */
public class Client {
    //proudy na soket komunikace
    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;
    //GUI prvky
    private Stage window;
    private Button buttonSend;
    private ComboBox<String> comboBox;
    private TextField textField;
    private TextArea textOnlineArea;
    private TextArea textChatArea;
    private Scene scene;
    //proud na revers cteni ze souboru pro historii chatu, externi apache knihovna
    private ReversedLinesFileReader logReader;
    //txt file s historii
    File log;

    public Client(Stage window) throws IOException {
        //inicializace GUI
        this.window = window;
        VBox vBox = new VBox(10);
        buttonSend = new Button("send");
        buttonSend.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        textField = new TextField();
        textOnlineArea = new TextArea();
        textOnlineArea.setEditable(false);
        textOnlineArea.setMaxSize(Double.MAX_VALUE, 30);
        textChatArea = new TextArea();
        textChatArea.setEditable(false);
        //combobox na zobrazeni historii chatu
        comboBox = new ComboBox<>();
        comboBox.getItems().addAll(
                "3",
                "10",
                "all"
        );
        comboBox.setEditable(true);
        comboBox.setMaxSize(Double.MAX_VALUE, 30);
        comboBox.setPromptText("Show history(last messages)");
        comboBox.setOnAction(event -> showLog());
        vBox.getChildren().addAll(comboBox, textOnlineArea, textChatArea, textField, buttonSend);
        scene = new Scene(vBox, 300, 300);
        window.setScene(scene);
        window.setTitle("chat v0.1");
        window.show();
        textField.requestFocus();


        try {
            //urceni IP adresy pocitace
            InetAddress ip = InetAddress.getLocalHost();
            textChatArea.appendText(ip.toString() + "\n");
            // pripojeni k serveru a inicializace soket proudu na komunikace mezi klienty a serverem
            socket = new Socket(ip, Const.Port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            //ziskavani absolutni cesty na .txt soubor s historii chatu od serveru
            log = new File(in.readLine());

            //eventlistener na odesilani zprav do chatu
            textField.setOnAction(e -> {
                out.println(textField.getText());
                textField.clear();
            });
            //event listener na tlacitko
            buttonSend.setOnAction(event -> {
                out.println(textField.getText());
                textField.clear();
            });
            //dotaz na nickname
            textChatArea.appendText("Enter your nickname" + "\n");

            // spusteni prijmu zprav od serveru(samostatne vlakno)
            Resender resend = new Resender();
            resend.start();

            //eventlistener na zavirani okinka s chatem, klient se odpoji od serveru a program klienta se skonci
            window.setOnCloseRequest(event -> {
                resend.setStop();
                out.println("exit");
                close();
                System.exit(0);
            });
        } catch (ConnectException e) {
            System.out.println("Server isn't running");
            System.out.println("Exeption: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //metoda na zobrazeni historie
    private void showLog() {
        try {
            String line;
            //list na vkladani prectenych radku ze souboru
            List<String> temp = new ArrayList<>();
            //inicializace readeru z txt souboru z historie
            logReader = new ReversedLinesFileReader(log);
            //ziskavame hodnotu z comboboxu
            String value = comboBox.getValue();
            //podle ziskane hodnoty zobrazime na textChatArea co je zapotrebi z historie
            try {
                if (value.equals("all")) {
                    while ((line = logReader.readLine()) != null) {
                        temp.add(line);
                    }
                } else if (!value.equals("")) {
                    int i = 0;
                    while ((line = logReader.readLine()) != null && i < Integer.parseInt(value)) {
                        temp.add(line);
                        i++;
                    }
                }
                textChatArea.clear();
                for (int s = temp.size() - 1; s >= 0; s--) {
                    textChatArea.appendText(temp.get(s) + "\n");
                }
                //zavirame proud
                logReader.close();
                //odhytavame numberformatexeption a zobrazujeme varovani v samostatnem okne
            } catch (NumberFormatException e) {

                comboBox.setValue("");
                logReader.close();

                Stage alertWindow = new Stage();
                alertWindow.initModality(Modality.APPLICATION_MODAL);
                alertWindow.setTitle("Error");
                alertWindow.setMinWidth(300);
                alertWindow.setMinHeight(200);
                Label label = new Label();
                label.setText("only integers are allowed");
                Button closeButton = new Button("OK");
                closeButton.setMinWidth(50);
                closeButton.setOnAction(es -> alertWindow.close());

                VBox layout = new VBox(10);
                layout.getChildren().addAll(label, closeButton);
                layout.setAlignment(Pos.CENTER);

                Scene allerScene = new Scene(layout);
                alertWindow.setScene(allerScene);
                alertWindow.showAndWait();
            }
        } catch (IOException e) {
            System.out.println("ReversedLinesFileReader exception");
            System.out.println("Exception: " + e);
        } catch (NullPointerException e) {
            System.out.println("Log file is not found");
            System.out.println("Exception: " + e);
        }

    }


    /**
     * zavirame vsechy proudy, soket a hlavni okno
     */
    private void close() {
        try {
            in.close();
            out.close();
            socket.close();
            window.close();
        } catch (Exception e) {
            System.err.println("Streams were not closed.");
        }
    }

    /**
     * Zobrazani vsech zprav ze serveru v jednotlivem vlaknu, funguje dosud stopet=false
     */
    private class Resender extends Thread {

        private boolean stoped;

        /**
         * ukonci ziskavani zprav ze serveru
         */
        public void setStop() {
            stoped = true;
        }

        /**
         * spusteni vlakna
         */
        @Override
        public void run() {
            try {
                while (!stoped) {
                    String str = in.readLine();
                    //zobrazeni uzivatelu chatu kteri jsou online
                    if (str != null && str.split(" ")[0].equals("#online")) {
                        textOnlineArea.clear();
                        textOnlineArea.appendText(str);
                        System.out.println(str);
                    } else if (str != null) {
                        textChatArea.appendText(str + "\n");
                        //pro pripad poku uzivatel zada "exit"
                    } else if (str == null) {
                        textChatArea.appendText("you've left");
                        System.out.println(str);
                        return;
                    }
                }
            } catch (IOException e) {
                System.err.println("Server error");
                System.err.println("Exception: " + e);
            }
        }
    }
}
