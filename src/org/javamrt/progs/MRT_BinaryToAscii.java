// This file is part of java-mrt
// A library to parse MRT files

// This file is released under LGPL 3.0
// http://www.gnu.org/licenses/lgpl-3.0-standalone.html

package org.javamrt.progs;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.javamrt.mrt.AS;
import org.javamrt.mrt.BGPFileReader;
import org.javamrt.mrt.Bgp4Update;
import org.javamrt.mrt.KeepAlive;
import org.javamrt.mrt.MRTRecord;
import org.javamrt.mrt.Notification;
import org.javamrt.mrt.Open;
import org.javamrt.mrt.Prefix;
import org.javamrt.mrt.PrefixMaskException;
import org.javamrt.mrt.RFC4893Exception;
import org.javamrt.mrt.StateChange;
import org.javamrt.mrt.TableDump;
import org.javamrt.utils.Debug;
import org.javamrt.utils.GetOpts;

public class MRT_BinaryToAscii {

	private static Prefix prefix = null;
	private static InetAddress peer = null;
	private static AS      originator = null;
	private static AS      traverses = null;
	private static boolean showIPv4 = true;
	private static boolean showIPv6 = true;
	private static boolean printRFC4893violations = false;

	public static void main(String args[]) {
		BGPFileReader in;
		MRTRecord record;
		GetOpts prueba;

		if (Debug.compileDebug)
			prueba = new GetOpts(args, "46DhmP:p:o:t:v");
		else
			prueba = new GetOpts(args, "46hmP:p:o:v:t:");

		char opcion;

		boolean oldall = false;

		while ((opcion = prueba.nextOption()) != 0) {
			try {
				switch (opcion) {
				case '4':
					if (showIPv4 == true && showIPv6 == true)
						showIPv6 = false;
					else
						System.exit(usage(1));
					break;

				case '6':
					if (showIPv4 == true && showIPv6 == true)
						showIPv4 = false;
					else
						System.exit(usage(1));
					break;
				case 'D':
					Debug.setDebug(true);
					break;
				case 'm':
					oldall = true;
					break;
				case 'p':
					peer = InetAddress.getByName(prueba.optarg);
					break;

				case 'P':
					prefix = Prefix.parseString(prueba.optarg);
					break;

				case 'v':
					printRFC4893violations  = true;
					break;

				case 'o':
					originator = AS.parseString(prueba.optarg);
					break;
				case 't':
					traverses = AS.parseString(prueba.optarg);
					break;
				case 'h':
				default:
					System.exit(usage((opcion) == 'h' ? 0 : 1));
					break;
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			} catch (PrefixMaskException e) {
				e.printStackTrace();
				System.exit(1);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		if (args.length == prueba.optind)
			System.exit(usage(0));

		for (int arg = prueba.optind; arg < args.length; arg++) {
			try {
				in = new BGPFileReader(args[arg]);
				while (false == in.eof()) {
				try {
					if ((record = in.readNext()) == null)
						break;
						if (record instanceof Open
								|| record instanceof KeepAlive
								|| record instanceof Notification)
							continue;
						if (record instanceof StateChange) {
							if (oldall == true && checkPeer(record)) {
								System.out.println(record.toString());
								continue;
							}
						}
						if ((record instanceof TableDump)
								|| (record instanceof Bgp4Update)) {

							if (!showIPv4 && record.getPrefix().isIPv4())
								continue;
							if (!showIPv6 && record.getPrefix().isIPv6())
								continue;
							try {
								if (!checkPrefix(record))
									continue;
								if (!checkPeer(record))
									continue;
								if (!checkASPath(record))
									continue;
							} catch (Exception e) {
								e.printStackTrace(System.err);
								System.err.printf("record = %s\n",record);
							}
							System.out.println(record);
						}
					} catch (RFC4893Exception rfce) {
						if (printRFC4893violations)
							System.err.println(rfce.toString());
					} catch (Exception e) {
						e.printStackTrace(System.err);
					}
				}
				in.close();
			} catch (java.io.FileNotFoundException e) {
				System.out.println("File not found: " + args[arg]);
			} catch (Exception ex) {
				System.err.println("Exception caught when reading " + args[arg]);
				ex.printStackTrace(System.err);
			}
		} // for (int arg...
	} // main()


	private static int usage(int retval) {
		PrintStream ps = System.err;

		ps.println("MRT_BinaryToAscii <options> f1 ...");
		ps.println("  -h        print this help message");
		ps.println("  -m        legacy compatibility wth MRT: include all records");
		ps.println("  -4        print IPv4 prefixes only");
		ps.println("  -6        print IPv6 prefixes only");
		ps.println("  -p peer   print updates from a specific peer only");
		ps.println("  -P prefix print updates for a specific prefix only");
		if (Debug.compileDebug)
			ps.println("  -D        enable debugging");
		ps.println("  -o as     print updates generated by one AS only");
		ps.println("  -t as     print updates where AS is in ASPATH");
		ps.println("         -4 and -6 together are not allowed");
		ps.println(" f1 ... are filenames or URL's");
		ps.println(" Use URL's according to the server's policies");
		ps.println(" Only prints records in machine readable format\n");

		return retval;
	}

	private static boolean checkPrefix(MRTRecord mrt) {
		if (prefix == null)
			return true;
		return prefix.equals(mrt.getPrefix());
	}

	private static boolean checkPeer(MRTRecord mrt) {
		if (peer == null)
			return true;
		return peer.equals(mrt.getPeer());
	}

	private static boolean checkASPath(MRTRecord mrt) {
		if (originator == null) {
			if (traverses == null)
				return true;
			//
			// check whether AS is traversed by the prefix
			//
			return mrt.getASPath().contains(traverses);
		}
		//
		// check whether the AS originates the prefix
		//
		return originator.equals(mrt.getASPath().generator());
	}
}