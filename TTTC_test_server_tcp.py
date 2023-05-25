import socket

HOST = "localhost"
PORT = 31161

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((HOST, PORT))
    print("Listening on ", HOST, PORT)
    sock.listen()
    while True:
        conn, addr = sock.accept()
        with conn:
            print("Connected to", addr)
            command = "whatever"
            while command != "quit":
                #data = conn.recv(2048)
                #print("<", data.decode("ascii"))

                command = input("> ")
                if command != "quit":
                    send_back = (command + "\r\n").encode("ascii")
                    conn.sendall(send_back)