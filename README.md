# KOPR concurrent projekt

#### Create a client-server application to copy the directory
---

## Technical requirements:
- Downloading a directory (directory and file tree) in parallel over a number of TCP sockets specified by the user.
- Copying only from the server to the client
- One file is always moving through only one socket, after its transfer, this socket sends another file, if there is still something that needs to be sent
- This download is interruptible by shutting down the server (simulating the loss of connection), or the client (simulating the user having to shut down / restart the computer unexpectedly). After re-establishing the connection between the server and the client, the client has the option to continue copying from the moment of interruption (already downloaded parts will not be downloaded again) in parallel over a given number of TCP sockets.
- GUI for the client in the JavaFX framework containing at least
   - Progressbar showing the percentage of copying the number of files
   - Progressbar showing the percentage of copied data size in MB
   - Button to start copying
   - Button to resume copying if (at the startup) it is detected, that copying has been interrupted
- The program should be able to copy on the local network, or within localhost even 1 GB of medium-sized files in less than 1 minute
- The project must use Executor to manage threads - you do not create your own threads
- Use at least one synchronizer
- Catch an interrupt event in the task
- The number of TCP connections created during the whole copying period must be equal to the number of TCP sockets entered by the user with a possible bonus one TCP socket / connection for copy management (if you need to use it) - no new TCP connections are created during copying
- It is forbidden to use `Thread.sleep` instead of a suitable synchronizer
--
## How it works
General architecture:

1. Server crawls whole directory and stores all found files into shared between threads `java.util.concurrent.BlockingQueue`.
2. Server starts *n* threads via `java.util.concurrent.ExecutorService`.
3. Every `java.lang.Thread` owns `java.net.Socket` or stream  `socket.getOutputStream()`/`socket.getInputStream()`.
4. Each thread takes file from the queue, then sends it. This is repeated till `BlockingQueue` is not empty. As soon as thread sees that `BlockingQueue` is empty, it sends 'poison pill' .
5. Client starts *n* `javafx.concurrent.Task` via `javafx.concurrent.Service`.
6. Each `Task` runs until it gets a 'poison pill' .
7. After each received chunk of file, client updates it's 'downloaded data progress bar'.
8. After each received file, client updates it's 'downloaded files progress bar'.

![general](https://user-images.githubusercontent.com/53663457/99918527-c6ef4780-2d17-11eb-8f2b-ef10f2916f3d.png)

From Establishing connection to data sending:

1. In order to let server know how many connections it's `java.net.ServerSocket` should expect, which files or how many bytes of them have been delivered, what client wants: start from the beginning or continue downloading, etc., we need 'managing socket'.  
2. After establishing connection with 'managing socket', server exchanges 'meta information', mentioned above, with a client.
3. Then we can establish connections with file-transfer sockets.
4. Finally, we can start getting files from the server in the following format:

   1. server sends file length
   2. server sends file's path, where it is going to be saved
   3. cyclically sends byte arrays

![exchange](https://user-images.githubusercontent.com/53663457/100156713-ad7e0500-2ea9-11eb-831c-a0ec099f9320.png)

## May help
``` java
// DataStreams can read and write data types such as int, boolean, string... 
DataInputStream dis = new DataInputStream(...);
DataOutputStream dos = new DataOutputStream(...);
dos.writeInt(1);
dos.writeUTF("Hello");
int i = dis.readInt();
String s = dis.readUTF();
```
``` java
// ObjectStreams can read and write serializable objects (class x implements Serializable)
ObjectOutputStream oos = new ObjectOutputStream(...);
oos.writeObject(new Person("Tomas"));
ObjectInputStream ois = new ObjectInputStream(...);
Person p = (Person)ois.readObject();
```
``` java
// RandomAccessFile can read and write to files with any offset  
RandomAccessFile raf = new RandomAccessFile(file, "rw"); // rw = read write
raf.seek(10); // moves file cursor by 10 bytes
raf.write(...);
```
