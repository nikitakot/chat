package server;

import main.Const;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by kotn0 on 16.11.2016.
 */
public class Server {
    //list na ukladani jmen online uzivatelu
    private List<String> online = new ArrayList<>();

    //synchronized list pro praci se spojeni
    private List<Connection> connections =
            Collections.synchronizedList(new ArrayList<Connection>());
    private ServerSocket server;

    //inicializace souboru na historie chatu
    private File history = new File("log.txt");
    //proud na ulozeni zprav do souboru s hisrorii
    private PrintWriter log;

    public Server() {
        try {
            //vytvareni serveru a inicializace proudu na zalohovani
            server = new ServerSocket(Const.Port);
            log = new PrintWriter(new FileOutputStream(history, true));

            //pripojeni a spusteni klientu v nekonecnem ciklu, vypne se pouze pri nejake chybe
            while (true) {
                Socket socket = server.accept();

                Connection con = new Connection(socket);
                connections.add(con);

                con.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }

    //zavirani vsech proudu
    private void closeAll() {
        try {
            server.close();
            log.close();

            synchronized (connections) {
                Iterator<Connection> iter = connections.iterator();
                while (iter.hasNext()) {
                    ((Connection) iter.next()).close();
                }
            }
        } catch (Exception e) {
            System.err.println("Streams were not closed");
        }
    }

    private class Connection extends Thread {
        //vstupni a vystuoni proudy na prace s klienty
        private BufferedReader in;
        private PrintWriter out;

        private Socket socket;
        private String name = "";

        //inicializace proudu
        public Connection(Socket socket) {
            this.socket = socket;

            try {
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
        }

        //spusteni vlakna
        @Override
        public void run() {
            try {
                //odesilani absolutni cesty k log souboru s historii
                out.println(history.getAbsoluteFile());
                //ziskavani jmena
                name = in.readLine();
                //pro pripad pokud uzivatel vypne program bez zadavani jmena
                if (name.equals("exit")) {
                    close();
                    return;
                }

                //upozorneni o novem uzivatele online
                synchronized (connections) {
                    online.add(name);
                    String date = "[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "] ";
                    Iterator<Connection> iter = connections.iterator();
                    while (iter.hasNext()) {
                        Connection connection = iter.next();
                        connection.out.println(date + name + " comes now");
                        connection.out.println("#online " + online);
                    }
                    log.println(date + name + " comes now");
                    log.flush();
                }

                //cyklus na odpojeni klienta
                String str = "";
                while (true) {
                    try {
                        str = in.readLine();
                        if (str.equals("exit")) break;
                    } catch (SocketException e) {
                        System.err.println("Client " + name + " stopped chat process");
                        System.err.println("Exception " + e);
                        break;
                    }

                    //cyklus na zasilani zprav
                    synchronized (connections) {
                        String date = "[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "] ";
                        Iterator<Connection> iter = connections.iterator();
                        while (iter.hasNext()) {
                            ((Connection) iter.next()).out.println(date + name + ": " + str);
                        }
                        log.println(date + name + ": " + str);
                        log.flush();
                    }
                }

                //odpojeni klienta
                synchronized (connections) {
                    online.remove(name);
                    String date = "[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "] ";
                    Iterator<Connection> iter = connections.iterator();
                    while (iter.hasNext()) {
                        Connection connection = iter.next();
                        connection.out.println(date + name + " has left");
                        connection.out.println("#online " + online);
                    }
                    log.println(date + name + " has left");
                    log.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close();
            }
        }

        //zavirani proudu a soketu
        public void close() {
            try {
                in.close();
                out.close();
                socket.close();

                connections.remove(this);
                //vypinani serveru pro pripad ze ani jeden klient nezustal pripojeny
//                if (connections.size() == 0) {
//                    Server.this.closeAll();
//                    System.exit(0);
//                }
            } catch (Exception e) {
                System.err.println("Streams were not closed");
                System.err.println("Exception " + e);
            }
        }
    }
}
