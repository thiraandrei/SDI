import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class Main {

	static int regPort = Configurations.REG_PORT;
	static Registry registry ;
        
	/**
	 * respawns replica servers and register replicas at master
	 * @param master
	 * @throws IOException
	 */
	static void respawnReplicaServers(Master master)throws IOException{
		System.out.println("[@main] respawning replica servers ");
		// TODO make file names global
		BufferedReader br = new BufferedReader(new FileReader("repServers.txt"));
		int n = Integer.parseInt(br.readLine().trim());
		ReplicaLoc replicaLoc;
		String s;

		for (int i = 0; i < n; i++) {
			s = br.readLine().trim();
			replicaLoc = new ReplicaLoc(i, s.substring(0, s.indexOf(':')) , true);
			ReplicaServer rs = new ReplicaServer(i, "./"); 

			ReplicaInterface stub = (ReplicaInterface) UnicastRemoteObject.exportObject(rs, 0);
			registry.rebind("ReplicaClient"+i, stub);

			master.registerReplicaServer(replicaLoc, stub);

			System.out.println("replica server state [@ main] = "+rs.isAlive());
		}
		br.close();
	}

	public static void launchClients() throws IOException{
		try {
                      while(true==true){
                          BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                            String numar = reader.readLine();
                            int nr =Integer.parseInt(numar);
                            switch (nr){
                                case 100:
                                    System.out.println("Wrong command");
                                    break;
                                case 1:
                            // creare+scriere fisier
                            char[] ss;
                            byte[] data;
                                Client c1 = new Client();
        System.out.println("Ati selectat comanda de creare a fisierului(introduceti numele fisierului in care doriti sa scrieti)" );
        System.out.println();
        String filename = reader.readLine();
        System.out.println("Introduceti textul: " );
        System.out.println();
        String text = reader.readLine();
        ss = text.toCharArray();
        data = new byte[ss.length];
        for (int i = 0; i < ss.length; i++) {
            data[i] = (byte) ss[i];
        }
        c1.write(filename, data);                               
                                    break;
                                case 2:
                                    // citire fisiere
                                    Client c = new Client();
                                    System.out.println("Introduceti numele fisierului pe care doriti sa-l cititi");
                          String fisier = reader.readLine();
                          String str = new String(c.read(fisier));
                          System.out.println("Continutul fisierului: ");
                          System.out.println(str);
                                    break;
                                case 3:
                                   // afisare continut folder
                                   Client c3 = new Client(); // creare client nou 
                                   File directoryPath = new File("C:\\Users\\thira\\Documents\\NetBeansProjects\\Proiect SDI");
                                           String contents[] = directoryPath.list();
                                           for (int i = 0; i < contents.length; i++)
                                     System.out.println(contents[i]);
                                    break;
                                case 4:
                                   // stergere fisiere
                                    System.out.println("Numele fisierului:");
                          String fisierdel = reader.readLine();
                          for (int x = 0; x < 3; x++) { 
            String fileName = "C:\\Users\\thira\\Documents\\NetBeansProjects\\Proiect SDI\\Replica_" + x + "\\" + fisierdel;
                          System.out.println("Fisierul a fost sters");
                          Files.deleteIfExists(Paths.get(fileName));
                          }
                                    break;
                                case 0:
                                    System.out.println( "Exit");
                                    System.exit(0);
                            }
                        }
		} catch (NotBoundException | IOException | MessageNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * runs a custom test as follows
	 * 1. write initial text to "file1"
	 * 2. reads the recently text written to "file1"
	 * 3. writes a new message to "file1"
	 * 4. while the writing operation in progress read the content of "file1"
	 * 5. the read content should be = to the initial message
	 * 6. commit the 2nd write operation
	 * 7. read the content of "file1", should be = initial messages then second message 
	 * 
	 * @throws IOException
	 * @throws NotBoundException
	 * @throws MessageNotFoundException
	 */
	public  static void customTest() throws IOException, NotBoundException, MessageNotFoundException{
		Client c = new Client();
		String fileName = "file1";

		char[] ss = "[INITIAL DATA!]".toCharArray(); // len = 15
		byte[] data = new byte[ss.length];
		for (int i = 0; i < ss.length; i++) 
			data[i] = (byte) ss[i];

		c.write(fileName, data);

		c = new Client();
		ss = "File 1 test test END".toCharArray(); // len = 20
		data = new byte[ss.length];
		for (int i = 0; i < ss.length; i++) 
			data[i] = (byte) ss[i];

		
		byte[] chunk = new byte[Configurations.CHUNK_SIZE];

		int seqN =data.length/Configurations.CHUNK_SIZE;
		int lastChunkLen = Configurations.CHUNK_SIZE;

		if (data.length%Configurations.CHUNK_SIZE > 0) {
			lastChunkLen = data.length%Configurations.CHUNK_SIZE;
			seqN++;
		}
		
		WriteAck ackMsg = c.masterStub.write(fileName);
		ReplicaServerClientInterface stub = (ReplicaServerClientInterface) registry.lookup("ReplicaClient"+ackMsg.getLoc().getId());

		FileContent fileContent;
		@SuppressWarnings("unused")
		ChunkAck chunkAck;
		//		for (int i = 0; i < seqN; i++) {
		System.arraycopy(data, 0*Configurations.CHUNK_SIZE, chunk, 0, Configurations.CHUNK_SIZE);
		fileContent = new FileContent(fileName, chunk);
		chunkAck = stub.write(ackMsg.getTransactionId(), 0, fileContent);


		System.arraycopy(data, 1*Configurations.CHUNK_SIZE, chunk, 0, Configurations.CHUNK_SIZE);
		fileContent = new FileContent(fileName, chunk);
		chunkAck = stub.write(ackMsg.getTransactionId(), 1, fileContent);

		// read here 
		List<ReplicaLoc> locations = c.masterStub.read(fileName);
		System.err.println("[@CustomTest] Read1 started ");

		// TODO fetch from all and verify 
		ReplicaLoc replicaLoc = locations.get(0);
		ReplicaServerClientInterface replicaStub = (ReplicaServerClientInterface) registry.lookup("ReplicaClient"+replicaLoc.getId());
		fileContent = replicaStub.read(fileName);
		System.err.println("[@CustomTest] data:");
		System.err.println(new String(fileContent.getData()));


		// continue write 
		for(int i = 2; i < seqN-1; i++){
			System.arraycopy(data, i*Configurations.CHUNK_SIZE, chunk, 0, Configurations.CHUNK_SIZE);
			fileContent = new FileContent(fileName, chunk);
			chunkAck = stub.write(ackMsg.getTransactionId(), i, fileContent);
		}
		// copy the last chuck that might be < CHUNK_SIZE
		System.arraycopy(data, (seqN-1)*Configurations.CHUNK_SIZE, chunk, 0, lastChunkLen);
		fileContent = new FileContent(fileName, chunk);
		chunkAck = stub.write(ackMsg.getTransactionId(), seqN-1, fileContent);

		
		
		//commit
		ReplicaLoc primaryLoc = c.masterStub.locatePrimaryReplica(fileName);
		ReplicaServerClientInterface primaryStub = (ReplicaServerClientInterface) registry.lookup("ReplicaClient"+primaryLoc.getId());
		primaryStub.commit(ackMsg.getTransactionId(), seqN);

		
		// read
		locations = c.masterStub.read(fileName);
		System.err.println("[@CustomTest] Read3 started ");

		replicaLoc = locations.get(0);
		replicaStub = (ReplicaServerClientInterface) registry.lookup("ReplicaClient"+replicaLoc.getId());
		fileContent = replicaStub.read(fileName);
		System.err.println("[@CustomTest] data:");
		System.err.println(new String(fileContent.getData()));

	}

	static Master startMaster() throws AccessException, RemoteException{
		Master master = new Master();
		MasterServerClientInterface stub = 
				(MasterServerClientInterface) UnicastRemoteObject.exportObject(master, 0);
		registry.rebind("MasterServerClientInterface", stub);
		System.err.println("Server ready");
		return master;
	}

	public static void main(String[] args) throws IOException {


		try {
			LocateRegistry.createRegistry(regPort);
			registry = LocateRegistry.getRegistry(regPort);

			Master master = startMaster();
			respawnReplicaServers(master);

//			customTest();
			launchClients();

		} catch (RemoteException   e) {
			System.err.println("Server exception: " + e.toString());
			e.printStackTrace();
		}
	}

}
