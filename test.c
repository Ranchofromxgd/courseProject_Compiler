

def int factor( int N ){
	if( N > 1 ){
		return N * cal factor( N - 1 );
	}
	else{
		return 1;
	}
}

def void main(int argtest)
{
                                                //ARRAY TEST
    array s[100];
    int a,times;              //test whether the offset is set correctly
    array s2[4];
    println("==========");
    s2[1] = 100;
    a = 456;
    s[3] = 655*a + s2[1];    //test the load and save of array element
    argtest = 456;
    s[3] = 655*argtest + s2[1] ;  //test the load and save of array element
    println(s[3]);

                                                //RECURSIVE TEST
    times = 6;
    a = cal factor(times);
    println("Result of recursive test:");
    println(a);
                                                //CONST TEST

}