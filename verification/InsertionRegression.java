import com.tacz.guns.compat.jyvr.InsertionGate;
public class InsertionRegression {
 static int checks;static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  InsertionGate gate=new InsertionGate();
  for(int i=0;i<10;i++)check(!gate.update(true,0,0,1,.1),"no immediate snap on spawn");
  check(!gate.update(true,-.12,0,1,.1),"outside entrance arms approach");
  for(int i=0;i<5;i++)check(!gate.update(true,-.099,0,1,.1),"touching entrance is not seating");
  check(!gate.update(true,-.07,0,1,.1),"push inward first tick");
  check(!gate.update(true,-.07,0,1,.1),"push inward second tick");
  check(gate.update(true,-.07,0,1,.1),"push inward third tick seats");
  for(int i=0;i<10;i++)check(gate.update(true,-.07,0,1,.1),"holds readiness across packet cooldown");
  gate.reset();check(!gate.update(true,-.07,0,1,.1),"ack clears readiness");
  check(!gate.update(true,-.12,0,1,.1),"outside again");
  for(int i=0;i<5;i++)check(!gate.update(true,-.07,0,.7,.1),"tilted magazine rejected");
  for(int i=0;i<5;i++)check(!gate.update(true,-.07,.02,1,.1),"side contact rejected");
  check(!gate.update(true,Double.NaN,0,1,.1),"bad tracking resets");
  check(!gate.update(true,-.07,0,1,.1),"invalid tracking cannot arm");
  check(!InsertionGate.aligned(.02,0,1,.1),"over-insertion rejected");
  check(!InsertionGate.aligned(-.11,0,1,.1),"outside mouth rejected");
  check(InsertionGate.aligned(-.01,0,1,.02f),"short socket supported");
  check(InsertionGate.aligned(-.17,0,1,.2f),"long socket supported");
  System.out.println("PASS: "+checks+" insertion checks. No headset integration covered.");
 }
}
