/**
 * THIS SMART CONTRACT IS NOT TESTED
 * 
 * DO NOT USE ON MainNet
 * 
 * @author Malcolm Lerider
 * 
 */

import java.math.BigInteger;
import org.neo.smartcontract.framework.SmartContract;
import org.neo.smartcontract.framework.services.neo.Blockchain;
import org.neo.smartcontract.framework.services.neo.Storage;
import org.neo.smartcontract.framework.services.neo.Transaction;
import org.neo.smartcontract.framework.services.neo.TransactionOutput;
import org.neo.smartcontract.framework.services.neo.Runtime;
import org.neo.smartcontract.framework.services.system.ExecutionEngine;

public class ICO_template extends SmartContract {
	
	//Token Settings
	public static String name = "name of the token";
	public static String symbol = "SymbolOfTheToken";
	public static final byte[] Owner =  //public key or script hash
			{ 2, -99, 6, 102, 4, -41, 48, -96, -116, 23, 9, 72, -89, -104, -107, 2, -8, -70, -2, 96, 60, -21, 105, 105, -93, 103, -80, -113, 17, -61, 7, 20, -85 };
	public static byte decimals = 8;
	private final static long factor = 100000000; //decided by Decimals()

	//ICO Settings
	private static final byte[] neo_asset_id = { 97, 111, 51, 52, 110, 07, 05, 12, 34, 92, 74, 19, 86, 54, 29, 14, 14, 17, 33, 96, 10, 14, 47, 15, 14, 19, 116, 16, 28, 25, 124, 15 };
	private final static long total_amount = 100000000 * factor;
	private final static long pre_ico_cap = 0 * factor;
	private final static int ico_start_time = 1502726400;
	private final static int ico_end_time = 1503936000;

	[DisplayName("transfer")]
			public static event Action<byte[], byte[], BigInteger> Transferred;

	[DisplayName("refund")]
			public static event Action<byte[], BigInteger> Refund;

	public static Object Main(String operation, Params object[] args)
	{
		if (Runtime.Trigger == TriggerType.Verification)
		{
			if (Owner.Length == 20)
			{
				return Runtime.CheckWitness(Owner);
			}
			else if (Owner.Length == 33)
			{
				byte[] signature = operation.AsByteArray();
				return VerifySignature(signature, Owner);
			}
		}
		else if (Runtime.Trigger == TriggerType.Application)
		{
			if (operation == "deploy") return Deploy();
			if (operation == "mintTokens") return MintTokens();
			if (operation == "totalSupply") return TotalSupply();
			if (operation == "name") return Name();
			if (operation == "symbol") return Symbol();
			if (operation == "transfer")
			{
				if (args.Length != 3) return false;
				byte[] from = (byte[])args[0];
				byte[] to = (byte[])args[1];
				BigInteger value = (BigInteger)args[2];
				return Transfer(from, to, value);
			}
			if (operation == "balanceOf")
			{
				if (args.Length != 1) return 0;
				byte[] account = (byte[])args[0];
				return BalanceOf(account);
			}
			if (operation == "decimals") return Decimals();
		}
		return false;
	}

	// initialization parameters, only once
	// 初始化参数
	public static boolean Deploy()
	{
		byte[] total_supply = Storage.get(Storage.currentContext(), "totalSupply");
		if (total_supply.length != 0) return false;
		Storage.put(Storage.currentContext(), Owner, BigInteger.valueOf(pre_ico_cap));
		Storage.put(Storage.currentContext(), "totalSupply", BigInteger.valueOf(pre_ico_cap));
		Transferred(null, Owner, BigInteger.valueOf(pre_ico_cap));
		return true;
	}

	// The function MintTokens is only usable by the chosen wallet
	// contract to mint a number of tokens proportional to the
	// amount of neo sent to the wallet contract. The function
	// can only be called during the tokenswap period
	// 将众筹的neo转化为等价的ico代币
	public static boolean MintTokens()
	{
		Transaction tx = (Transaction)ExecutionEngine.scriptContainer();
		TransactionOutput reference = tx.references()[0];
		// check whether asset is neo
		// 检查资产是否为neo
		if (reference.assetId() != neo_asset_id) return false;
		byte[] sender = reference.scriptHash();
		TransactionOutput[] outputs = tx.outputs();
		byte[] receiver = ExecutionEngine.executingScriptHash();
		long value = 0;
		// get the total amount of Neo
		// 获取转入智能合约地址的Neo总量
		for (int i = 0; i < outputs.length; i++) 
		{
			if (outputs[i].scriptHash() == receiver)
			{
				value += (long) outputs[i].value();
			}
		}
		// the current exchange rate between ico tokens and neo during the token swap period
		// 获取众筹期间ico token和neo间的转化率
		long swap_rate = CurrentSwapRate();
		// crowdfunding failure
		// 众筹失败
		if (swap_rate == 0)
		{
			Refund(sender, value);
			return false;
		}
		// crowdfunding success
		// 众筹成功
		long token = value * swap_rate / 100000000;
		BigInteger balance = new BigInteger(Storage.get(Storage.currentContext(), sender));
		Storage.put(Storage.currentContext(), sender, balance.add(BigInteger.valueOf(token)));
		BigInteger totalSupply = new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
		Storage.put(Storage.currentContext(), "totalSupply", totalSupply.add(BigInteger.valueOf(token)));
		Transferred(null, sender, BigInteger.valueOf(token));
		return true;
	}

	// get the total token supply
	// 获取已发行token总量
	public static BigInteger TotalSupply()
	{
		return new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
	}

	// function that is always called when someone wants to transfer tokens.
	// 流转token调用
	public static boolean Transfer(byte[] from, byte[] to, BigInteger value)
	{
		if (value.compareTo(BigInteger.valueOf(0L)) < 1) return false;
		if (!Runtime.checkWitness(from)) return false;
		if (from == to) return true;
		BigInteger from_value = new BigInteger(Storage.get(Storage.currentContext(), from));
		if (from_value.compareTo(value) < 0) return false;
		if (from_value == value)
			Storage.delete(Storage.currentContext(), from);
		else
			Storage.put(Storage.currentContext(), from, from_value.subtract(value));
		BigInteger to_value = new BigInteger(Storage.get(Storage.currentContext(), to));
		Storage.put(Storage.currentContext(), to, to_value.add(value));
		Transferred(from, to, value);
		return true;
	}

	// get the account balance of another account with address
	// 根据地址获取token的余额
	public static BigInteger BalanceOf(byte[] address)
	{
		return new BigInteger(Storage.get(Storage.currentContext(), address));
	}

	// The function CurrentSwapRate() returns the current exchange rate
	// between ico tokens and neo during the token swap period
	private static long CurrentSwapRate()
	{
		final long basic_rate = 1000 * factor;
		final int ico_duration = ico_end_time - ico_start_time;
		BigInteger total_supply = new BigInteger(Storage.get(Storage.currentContext(), "totalSupply"));
		if (total_supply.compareTo(BigInteger.valueOf(total_amount)) >= 0) return 0;
		int now = Blockchain.getHeader(Blockchain.height()).timestamp();
		int time = (int)now - ico_start_time;
		if (time < 0)
		{
			return 0;
		}
		else if (time < 86400)
		{
			return basic_rate * 130 / 100;
		}
		else if (time < 259200)
		{
			return basic_rate * 120 / 100;
		}
		else if (time < 604800)
		{
			return basic_rate * 110 / 100;
		}
		else if (time < ico_duration)
		{
			return basic_rate;
		}
		else
		{
			return 0;
		}
	}
}

