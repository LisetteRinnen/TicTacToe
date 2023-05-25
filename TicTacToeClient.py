""" TicTacToeClient.py - Client for the TicTacToeProtocol (TTTP) for INFO 314
"""

import sys
import socket

class T3TransportLayerSocket:
    """ Handles low level transport for TTTP, as well as T3 message encodings.
    """
    MAX_BUF_SIZE = 2048

    def __init__(self, hostname, port, protocol):
        self.protocol = protocol
        self.server_addr = (hostname, port)

        self.sock = socket.socket(
            socket.AT_INET,
            socket.SOCK_STREAM if protocol == "TCP" else socket.SOCK_DGRAM
            )
    
    def t3encode(request_body):
        """ Encode the request body for the TTT protocol
            Input should not contain any newline characters
        """
        return request_body + "\r\n".encode("ascii")
    
    def t3decode(response_body):
        """ Decode the response body for the TTT protocol """
        return response_body.decode("ascii").strip()

    def send(self, request_body):
        """ Send the request body in plaintext (no newline characters) to the TTT server """
        request_body = self.t3encode(request_body)

        if self.protocol == "TCP":
            self.sock.send(request_body)
        elif self.protocol == "UDP":
            self.sock.sendto(request_body, self.server_addr)
    
    def recv(self):
        """ Recieve a response body from the TTT server as a List of arguments
            e.g. "MOVE GID1 5" -> ["MOVE", "GID1", "5"]
        """
        if self.protocol == "TCP":
            response_body = self.sock.recv(self.MAX_BUF_SIZE)
        elif self.protocol == "UDP":
            response_body = self.sock.recvfrom(self.MAX_BUF_SIZE, self.server_addr)
        
        return self.t3decode(response_body).split(" ")
    
    def close(self):
        """ Close the socket if necessary """
        if self.protocol == "TCP":
            self.sock.close()

class T3ProtocolClient:
    """ T3ProtocolClient handles the connection with the TTT server
        and is used to send/receive TTTP messages
    """

    def __init__(self, url, client_id):
        protocol, address = url.split("://")
        protocol = "TCP" if protocol == "t3tcp" else "UDP"

        hostname, port = address.split(":")
        port = int(port)

        self.sock = T3TransportLayerSocket(hostname, port, protocol)
        self.client_id = client_id

        self.session_id = None
        self.game_id = None

    def init_session(self, protocol_version=1):
        """ Create a TTT session """

        self.sock.send(f"HELO {protocol_version} {self.client_id}")
        sess, server_protocol_ver, session_id = self.sock.recv()

        if server_protocol_ver != protocol_version: raise Exception("Server does not match client protocol version")

        self.session_id = session_id
    
    def create_game(self):
        """ Create a TTT game"""
        self.sock.send(f"CREA {self.client_id}")
        jond, joined_client_id, joined_game_id = self.sock.recv()

        if joined_client_id != self.client_id: raise Exception("Received JOND response for wrong client ID")

        self.game_id = joined_game_id

    def list_games(self):
        """ List available TTT games """
        self.sock.send(f"LIST")
        gams, *open_games = self.sock.recv()

        return open_games
    
    def join_game(self, game_id):
        """ Join a TTT game by its ID """
        self.sock.send(f"JOIN {game_id}")
        jond, joined_client_id, joined_game_id = self.sock.recv()

        if joined_client_id != self.client_id: raise Exception("Received JOND response for wrong client ID")

        self.game_id = joined_game_id

    def stat_game(self, game_id):
        """ Get the state of the TTT game with the given ID """
        self.sock.send(f"STAT {game_id}")
        bord, player1, player2, next_player, board_state = self.sock.recv()

        board = T3BoardState(game_id, player1, player2, next_player, board_state)
        return str(board)
    
    def stat_current_game(self):
        """ Get the state of the TTT game currently being played """
        if self.game_id == None: raise Exception("stat_current_game() called when no current game is active")

        return self.stat_game(self.game_id)
    
    def wait_for_turn(self):
        """ Wait for it to be my turn in the TTT game, or for the game to be over """
        if self.game_id == None: raise Exception("wait_for_turn() called when no current game is active")

        my_turn = False
        game_over = False
        winner = None

        while (not game_over) or (not my_turn):
            message, *args = self.sock.recv()
            if message == "YRMV":
                game_id, moving_player_id = args
                my_turn = moving_player_id == self.client_id
            elif message == "TERM":
                game_over = True
                game_id, winner_id = args
                if winner_id != "KTHXBYE":
                    winner = winner_id

        return game_over, winner
    
    def make_move(self, desired_space):
        if self.game_id == None: raise Exception("make_move() called when no current game is active")
        success = True
        initial_state = self.stat_current_game()

        self.sock.send(f"MOVE {self.game_id} {desired_space}")

        bord, player1, player2, next_player, board_state = self.sock.recv()
        final_state = T3BoardState(game_id, player1, player2, next_player, board_state)
        if initial_state == final_state:
            success = False

        return success
    
    def end_session(self):
        """ End the TTT session """
        self.sock.send(f"GDBY {self.session_id}")
        self.sock.close()

class T3BoardState:
    def __init__(self, game_id, player1, player2, next_player, board_state):
        self.game_id = game_id
        self.player1 = player1
        self.player2 = player2
        self.next_player = next_player
        self.board = board
    
    def __str__(self):
        board = self.board.split("|")
        out = (
            f"--- Game {self.game_id} ---" +
            f"Crosses (X): {self.player1}" + (" [TURN]" if self.player1 == self.next_player else "") +
            f"Naughts (O): {self.player2}" + (" [TURN]" if self.player2 == self.next_player else "") +
            "\n" +
            f"{board[0]} {board[1]} {board[2]} " +
            "\n" +
            f"{board[3]} {board[4]} {board[5]} " +
            "\n" +
            f"{board[6]} {board[7]} {board[8]} "
            )

        return out
    
    def __eq__(self, __value):
        same_game = self.game_id == __value.game_id
        same_player1 = self.player1 == __value.player1
        same_player2 = self.player2 == __value.player2
        same_next_player = self.next_player == __value.next_player
        same_board = self.board == __value.board

        return same_game and same_player1 and same_player2 and same_next_player and same_board


### Main
print("Welcome to Tic Tac Toe Land.")
print("What server do you want to connect to?")
url = input("(default t3tcp://localhost:31161): ") or "t3tcp://localhost:31161"
print("What is your e-mail?")
client_id = input("(default alice@example.com): ") or "alice@example.com"

print("Thank you. Now connecting you to the Tic Tac Toe server at")
print("\t" + url)

client = T3ProtocolClient(url, client_id)
client.init_session()

try:
    while True:
        print("Would you like to create or join a game? create/join")
        action = input("(default create): ") or "create"
        if action == "create":
            client.create_game()
            print("Game created:")
        else:
            user_decided_on_game = False
            while not user_decided_on_game:
                available_games = client.list_games()
                print("Available games:")
                [print(f"\t - {game_id}") for game_id in available_games]
                print("Which game do you want to join?")
                game_id = input(f"(default: {available_games[0]}): ") or available_games[0]

                print(client.stat_game(game_id))

                print("Join this game? yes/no")
                user_decided_on_game = (input("(default yes): ") or "yes") == "yes"
            client.join_game(game_id)
            print("Game joined:")

        print(client.stat_current_game)
        game_over = False
        while not game_over:
            print("Waiting for turn...")
            game_over, winner = client.wait_for_turn()
            print(client.stat_current_game)
            if not game_over:
                move_made_successfully = False
                while not move_made_successfully:
                    print("Where do you want to go? (1-9, starting from top left)")
                    desired_space = input("(default 1): ") or "1"
                    move_made_successfully = client.make_move(desired_space)
        print(f"{winner} won the game!")
except KeyboardInterrupt:
    client.end_session()
    print("Goodbye!")