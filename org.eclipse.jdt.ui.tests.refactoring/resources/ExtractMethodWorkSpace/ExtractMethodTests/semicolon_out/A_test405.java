package semicolon_out;

public class A_test405 {
	public void foo() {
		/*]*/int x;
		extracted()/*[*/;
		x= 10;
	}
	protected void extracted() {
		int x= 0;
	}
}