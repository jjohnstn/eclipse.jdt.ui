package locals_out;
public class A_test543 {
	public void foo() {
		int i= 0;
		int[] array= new int[10];
		
		/*]*/i= extracted(array, i);/*[*/
		
		i++;
	}
	protected int extracted(int[] array, int i) {
		array[i++]= 10;
		return i;
	}
}
