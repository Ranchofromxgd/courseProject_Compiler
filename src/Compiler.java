/**
 * @Copyright
 * Author: Haohe Liu from NWPU
 * Time: April.2019
 * */

import com.sun.org.apache.bcel.internal.generic.RET;
import com.sun.org.apache.regexp.internal.RE;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.*;
import java.util.*;

/**
 * About the compiler:
 *  function:
 *      compiler the C-like language to MIPS instructions
 *  Error  report:
 *      Inside the switch-case block, throw error encountered
 *
 * The grammer we support now:
 *  Variable definations:
 *      All variable is considered as int type.Once we use a identifier, it will be considered defined
 *  Assignment statement:
 *      Assign a value(int,string,expression) to an idenetifier, if use a identifier before initialization, the variable will be
 *      automatically set 0
 *  Calculation:
 *      We support :
 *          Ops: +,-,*,/
 *          Order change: ()
 *  Select block:
 *      if-else:
 *          if(exp1){}
 *          else(exp2){}
 *      while:
 *          while(exp1){}
 *  Boolean expression:
 *      ==:equal
 *      >=:greater or equal than
 *      <=:less or equal than
 *      <:less than
 *      >:greater than
 *      Compound Boolean expression is temporarily not realized
 *  Comment:
 *      use double backslash to add comment:
 *          example: //comments
 *  Other built-in functions:
 *      println():
 *          print the number to output device
 *          print the string to output device
 *              the String can contain quotes, for example: "Compiler \\\" construction"
 * */

class Compiler
{
    public static void main(String[] args) throws IOException
    {

        if (args.length != 1)
        {
            //命令行需要有待编译的文件名
            System.err.println("Wrong number cmd line args");
            System.exit(1);
        }
        boolean debug = false;
        System.out.println("Directory: "+System.getProperty("user.dir"));
        //输入为一个.c--
        String inFileName = args[0];
        //输出为一个.a文件，可以为我们的assembler使用
        String outFileName = args[0] + ".a";
        //文件读取
        Scanner inFile = new Scanner(new File(inFileName));
        //文件输出
        PrintWriter outFile = new PrintWriter(outFileName);
        //符号表
        SymTab st = new SymTab();
        //词法分析器
        TokenMgr tm =  new TokenMgr(inFile);
        //语法分析器
        Parser parser = new Parser(st, tm, outFile);

        try
        {
            parser.parse();
        }
        //编译错误
        catch (RuntimeException e)
        {
            System.err.println(e.getMessage());
            outFile.println(e.getMessage());
            outFile.close();
            System.exit(1);
        }
        outFile.close();
    }
}


/**这个接口定义了各种我们可能使用到的标识符类型
 * 后续词法分析器和语法分析器等都是对这个接口的实现
 * */
interface Constants
{
    // integers that identify token kinds
    int EOF = 0;
    int PRINTLN = 1;
    int UNSIGNED = 2;
    int ID = 3;
    int ASSIGN = 4;
    int SEMICOLON = 5;
    int LEFTPAREN = 6;
    int RIGHTPAREN = 7;
    int PLUS = 8;
    int MINUS = 9;
    int TIMES = 10;
    int ERROR = 11;
    int DIVIDE = 12;
    int LEFTBRACE = 13;
    int RIGHTBRACE = 14;
    int STRING = 15;
    //switch expression
    int WHILE = 16;
    int IF = 17;
    int ELSE = 18;
    //boolean expression
    int EQUAL = 19;
    int GREATER_THAN = 20;
    int SMALLER_THAN = 21;
    int GREATER_EQUAL_THAN = 22;
    int SMALLER_EQUAL_THAN = 23;
    int INT = 24;
    int RETURN = 25;
    int DEF = 26;
    int VOID = 27;
    int CAL = 28;
    int AND = 29;
    int OR = 30;
    int COMMA = 31;
    int END = 32;


    // tokenImage provides string for each token kind
    String[] tokenImage =
            {
                    "<EOF>",
                    "\"println\"",
                    "<UNSIGNED>",
                    "<ID>",
                    "\"=\"",
                    "\";\"",
                    "\"(\"",
                    "\")\"",
                    "\"+\"",
                    "\"-\"",
                    "\"*\"",
                    "<ERROR>",
                    "\"/\"",
                    "\"{\"",
                    "\"}\"",
                    "<STRING>",
                    "\"while\"",
                    "\"if\"",
                    "\"else\"",
                    "\"==\"",
                    "\">\"",
                    "\"<\"",
                    "\">=\"",
                    "\"<=\"",
                    "int",
                    "return",
                    "def",
                    "void",
                    "cal",
                    "and",
                    "or",
                    ",",
                    "~"//FORCE END
            };
}



/**对于一个token,我们保留它的起始位置和终止位置
 * 同时保存它的类型和image(就是这个东西在.c--文件里边本身长什么样子)
 * 也存储下一个token的引用
 * */
class Token implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public int kind;
    //token开始的行
    public int beginLine;
    //token开始的列
    public int beginColumn;
    //token结束的行
    public int endLine;
    //token结束的列
    public int endColumn;
    //token的字符串镜像
    public String image;
    //token的值,变量的定义
    //对下一个token的引用,相当于C语言里边的next指针
    public Token next;

    //构造函数
    public Token() {}
    public Token(int kind)
    {
        this(kind, null);
    }
    public Token(int kind, String image)
    {
        this.kind = kind;
        this.image = image;
    }
    public String toString()
    {
        return image;
    }
}

/**用于存储词法分析中遇到的标识符
 * */

class FuncSymTab
{
    public String func_name;
    public int args_num;
    public int vars_num;
    public int reg_saved;
    private ArrayList<String> args;
    private ArrayList<String> vars;
    public FuncSymTab(String func_name)
    {
        this.args = new ArrayList<>();
        this.vars = new ArrayList<>();
        this.args_num = 0;
        this.vars_num = 0;
        this.func_name = func_name;
    }
    public int getSpace()
    {
        return 4*(2+this.args_num+this.vars_num);
    }
    //on parsing: enter the args
    public void argEnter(String arg)
    {
        int index = args.indexOf(arg);
        if(index<0)
        {
            args.add(arg);
            args_num++;
        }
        else genDf(arg);
    }
    public void varEnter(String var)
    {
        if(args.indexOf(var)>=0)genDf(var);
        int index = vars.indexOf(var);
        if(index<0)
        {
            vars.add(var);
            vars_num++;
        }
        else genDf(var);
    }
    public int varLength()
    {
        return this.vars.size();
    }
    public int argLength()
    {
        return this.args.size();
    }
    //locate the memory location of this arg
    public int argLocate(String arg)
    {
        return args.indexOf(arg);
    }

    public int varLocate(String arg)
    {
        return vars.indexOf(arg);
    }
    public void reset()
    {
        this.func_name = "";
        this.args.clear();
        this.vars.clear();
        this.vars_num = 0;
        this.args_num = 0;
    }
    //Exceptions
    private void genDf(String item)
    {
        throw new RuntimeException("\nError: "+item+" is already defined");
    }
    private void genNf(String item)
    {
        throw new RuntimeException("\nError: "+item+" not defined");
    }
}


class SymTab
{
    private ArrayList<String> symbol;
    private Map<String,FuncSymTab> func_tabs;
    //ArrayList: add & indexOf
    public SymTab()
    {
        symbol = new ArrayList<>();
        func_tabs = new HashMap<>();

    }
    public void enter(String s)
    {
        int index = symbol.indexOf(s);
        if (index < 0)
            symbol.add(s);
    }
    public void enterFunc(String func_name,FuncSymTab func)
    {
        if (!func_tabs.containsKey(func_name))
            func_tabs.put(func_name, func);
        else throw new RuntimeException("Error: Function \""+func_name+"\" has already defined");
    }

    //指定index的项目
    public String getSymbol(int index)
    {
        return symbol.get(index);
    }
    //查看当前符号表有几个项目
    public int getSize()
    {
        return symbol.size();
    }
}



class TokenMgr implements Constants
{
    private Scanner inFile;
    private char currentChar;
    private int currentColumnNumber;
    private int currentLineNumber;
    private String inputLine;     // holds 1 line of input
    private Token token;          // holds 1 token
    private StringBuffer buffer;  // token image built here
    private boolean inString;
    //-----------------------------------------
    public TokenMgr(Scanner inFile)
    {
        this.inFile = inFile;
        currentChar = '\n';        //  '\n' triggers read
        currentLineNumber = 0;
        buffer = new StringBuffer();
        inString = false;
    }

    //-----------------------------------------
    public Token getNextToken()
    {
        // skip whitespace
        while (Character.isWhitespace(currentChar))
            getNextChar();

        token = new Token();
        token.next = null;
        token.beginLine = currentLineNumber;
        token.beginColumn = currentColumnNumber;

        // check for EOF
        if (currentChar == EOF)
        {
            token.image = "<EOF>";
            token.endLine = currentLineNumber;
            token.endColumn = currentColumnNumber;
            token.kind = EOF;
        }
        else
            if (Character.isDigit(currentChar))
            {
                buffer.setLength(0);
                do
                {
                    buffer.append(currentChar);
                    token.endLine = currentLineNumber;
                    token.endColumn = currentColumnNumber;
                    getNextChar();
                } while (Character.isDigit(currentChar));
                token.image = buffer.toString();
                token.kind = UNSIGNED;
            }

            else
                if (Character.isLetter(currentChar))
                {
                    buffer.setLength(0);
                    do
                    {
                        buffer.append(currentChar);
                        token.endLine = currentLineNumber;
                        token.endColumn = currentColumnNumber;
                        getNextChar();
                    } while (Character.isLetterOrDigit(currentChar));
                    token.image = buffer.toString();

                    if (token.image.equals("println"))
                        token.kind = PRINTLN;
                    else
                    if (token.image.equals("while"))
                        token.kind = WHILE;
                    else
                    if (token.image.equals("if"))
                        token.kind = IF;
                    else
                    if (token.image.equals("int"))
                        token.kind = INT;
                    else
                    if (token.image.equals("return"))
                        token.kind = RETURN;
                    else
                    if (token.image.equals("def"))
                        token.kind = DEF;
                    else
                    if (token.image.equals("void"))
                        token.kind = VOID;
                    else
                    if (token.image.equals("cal"))
                        token.kind = CAL;
                    else
                    if (token.image.equals("and"))
                        token.kind = AND;
                    else
                    if(token.image.equals("else"))
                        token.kind = ELSE;
                    else
                    if (token.image.equals("or"))
                        token.kind = OR;
                    else  // not a keyword so kind is ID
                        token.kind = ID;
                }
                else if (currentChar == '"') {
                    boolean done = false;
                    inString = true;
                    int backslashCounter = 0;
                    buffer.setLength(0);  // clear buffer
                    while (!done) {
                        do  // build token image in buffer
                        {
                            if (currentChar == '\\'){
                                backslashCounter++;
                            }
                            buffer.append(currentChar);
                            getNextChar();
                            if (currentChar != '\\' && currentChar != '"'){
                                backslashCounter = 0;
                            }
                            try {
                                if (currentChar == '\\' && inputLine.charAt(currentColumnNumber+1) == '\n'){
                                    getNextChar();
                                }
                            } catch (Exception e) {
                                getNextChar();
                            }
                            if (currentChar == '\n' || currentChar == '\r') {
                                break;
                            }
                        } while (currentChar != '"');
                        if (currentChar =='"' && backslashCounter % 2 == 0) //quote precede with even number of backslash
                        {
                            done = true;
                            backslashCounter = 0;
                            buffer.append(currentChar);
                            token.kind = STRING;
                        }
                        else if (currentChar =='"' && backslashCounter % 2 != 0) {
                            backslashCounter = 0;
                            continue;
                        }
                        else
                            token.kind = ERROR;
                        token.endLine = currentLineNumber;
                        token.endColumn = currentColumnNumber;
                        getNextChar();
                        token.image = buffer.toString();
                        inString = false;
                    }
                }
                else  // process single-character token
                {
                    switch(currentChar)
                    {
                        case '=':
                            if(lookAhead(1) == '=')
                            {
                                token.kind = EQUAL;
                                getNextChar();
                                token.image = "==";
                            } else {
                                token.image = Character.toString(currentChar);
                                token.kind = ASSIGN;
                            }
                            break;
                        case '>':
                            if(lookAhead(1) == '=')
                            {
                                token.kind = GREATER_EQUAL_THAN;
                                getNextChar();
                                token.image = ">=";
                            }else {
                                token.image = Character.toString(currentChar);
                                token.kind = GREATER_THAN;
                            }
                            break;
                        case '<':
                            if(lookAhead(1) == '=')
                            {
                                token.kind = SMALLER_EQUAL_THAN;
                                getNextChar();
                                token.image = "<=";
                            }else
                            {
                                token.image = Character.toString(currentChar);
                                token.kind = SMALLER_THAN;
                            }
                            break;
                        case ';':
                            token.kind = SEMICOLON;
                            token.image = Character.toString(currentChar);
                            break;
                        case '(':
                            token.kind = LEFTPAREN;
                            token.image = Character.toString(currentChar);
                            break;
                        case ')':
                            token.kind = RIGHTPAREN;
                            token.image = Character.toString(currentChar);
                            break;
                        case '+':
                            token.kind = PLUS;
                            token.image = Character.toString(currentChar);
                            break;
                        case '-':
                            token.kind = MINUS;
                            token.image = Character.toString(currentChar);
                            break;
                        case '*':
                            token.kind = TIMES;
                            token.image = Character.toString(currentChar);
                            break;
                        case '/':
                            token.kind = DIVIDE;
                            token.image = Character.toString(currentChar);
                            break;
                        case '{':
                            token.kind = LEFTBRACE;
                            token.image = Character.toString(currentChar);
                            break;
                        case '}':
                            token.kind = RIGHTBRACE;
                            token.image = Character.toString(currentChar);
                            break;
                        case ',':
                            token.kind = COMMA;
                            token.image = Character.toString(currentChar);
                            break;
                        case '~':
                            token.kind = END;
                            token.image = Character.toString(currentChar);
                        default:
                            token.kind = ERROR;
                            token.image = Character.toString(currentChar);
                            break;
                    }

                    // save currentChar as String in token.image

                    // save token end location
                    token.endLine = currentLineNumber;
                    token.endColumn = currentColumnNumber;
                    getNextChar();  // read beyond end
                }

        return token;
    }
    private char lookAhead(int amount)
    {
        try
        {
            char next = inputLine.charAt(currentColumnNumber+amount-1);
            return next;
        }
        catch (Exception e){System.out.println("Error");}
        return ' ';
    }
    //-----------------------------------------
    private void getNextChar()
    {
        if (currentChar == EOF)
            return;

        if (currentChar == '\n')
        {
            if (inFile.hasNextLine())     // any lines left?
            {
                inputLine = inFile.nextLine();  // get next line
                inputLine = inputLine + "\n";   // mark line end
                currentLineNumber++;
                currentColumnNumber = 0;
            }
            else  // at EOF
            {
                currentChar = EOF;
                return;
            }
        }
        // check if single-line comment
        if (!inString &&
                inputLine.charAt(currentColumnNumber) == '/' &&
                inputLine.charAt(currentColumnNumber+1) == '/')
            currentChar = '\n';  // forces end of line
        else
            currentChar = inputLine.charAt(currentColumnNumber++);
    }
}

/**
 * Bounding each .ascii2 string with a identifier -> MIPS
 * */
class StringMgr
{
    private ArrayList<String> collection;
    private int stringcount;

    public StringMgr()
    {
        this.stringcount = 0;
        this.collection = new ArrayList<>();
    }
    private String stringIdAvailable()
    {
        return "Str"+this.stringcount++;
    }
    public String enter(String str)
    {
        String str_id = stringIdAvailable();
        collection.add(str);
        return str_id;
    }
    public int getSize()
    {
        return collection.size();
    }
    public String getItem(int index)
    {
        return collection.get(index);
    }
}

class Parser implements Constants
{
    private SymTab st;
    private TokenMgr tm;
    private PrintWriter outFile;
    private Token currentToken;
    private Token previousToken;
    private int identifiercount;
    private int registercount;
    private int registerS_count;
    private int registerA_count;
    private StringMgr sm;
    private ArrayList<String> StringIdentifiers;
    private FuncSymTab ft;
    //the function we are working in
    private String currentfunction;
    public Parser(SymTab st, TokenMgr tm, PrintWriter outFile)
    {
        this.st = st;
        this.tm = tm;
        this.outFile = outFile;
        this.identifiercount = 0;

        this.registercount = 0;
        this.registerS_count = 0;
        this.registerA_count = 0;

        this.sm = new StringMgr();
        this.StringIdentifiers = new ArrayList<>();

        this.currentfunction = "main";
        //This instance is shared between different functions, after parsing one, it will be reset
        this.ft = new FuncSymTab("main");

        currentToken = tm.getNextToken();
        previousToken = null;
    }


    private void emitInstruction(String func,String op1)
    {
        outFile.println(func+"\t"+op1);
    }
    private void emitInstruction(String func,String op1,String op2)
    {
        outFile.println(func+"\t"+op1+",\t"+op2);
    }
    private void emitInstruction(String func,String op1,String op2,String op3)
    {
        outFile.println(func+"\t"+op1+",\t"+op2+",\t"+op3);
    }

    private String registerAvailable()
    {
        String temp = "$t"+this.registercount++;
        //Totally we have $t0~$t9, so if we don't have enough register, we will throw an exception
        if(this.registercount == 11)
        {
            throw genEx("Temporary registor overflow");
        }
        return temp;
    }

    private String registerS_Available(){
        String temp = "$s"+this.registerS_count++;
        if(this.registerS_count == 9)
        {
            throw genEx("s registor overflow");
        }
        return temp;
    }

    private String registerA_Available(){
        String temp = "$a"+this.registerA_count++;
        if(this.registerA_count == 5)
        {
            throw genEx("Registor a overflow");
        }
        return temp;
    }

    /**
     * registerAvailable: return a free register
     * resetRegister: clear and reset the use of registers
     * identifierAvailable: Generate a label for jump instruction
     * */

    private void resetRegister()
    {
        this.registerS_count = 0;
        this.registercount = 0;
        this.registerA_count = 0;
    }
    private String identifierAvailable(){ return "L"+this.identifiercount++;}

    private RuntimeException genEx(String errorMessage)
    {
        return new RuntimeException("Encountered \"" +
                currentToken.image + "\" on line " +
                currentToken.beginLine + ", column " +
                currentToken.beginColumn + "." +
                errorMessage);
    }
    private void advance()
    {
        previousToken = currentToken;
        if (currentToken.next != null)
            currentToken = currentToken.next;
        else
            currentToken = currentToken.next = tm.getNextToken();
    }
    private void consume(int expected)
    {
        if (currentToken.kind == expected)
            advance();
        else
            throw genEx("Expecting " + tokenImage[expected]);
    }


    public void parse()
    {
        program();
    }

    private void programUnitList()
    {
        switch (currentToken.kind)
        {
            case DEF:
                programUnit();
                programUnitList();
                break;
            default:
                break;
        }
    }

    private void programUnit()
    {
        switch (currentToken.kind)
        {
            case DEF:
                functionDefinition();
                break;
            default:
                break;
        }
    }

    private void functionDefinition()
    {
            consume(DEF);
            consume(VOID);
            //Entrance of a function
            outFile.println(currentToken.image+":");
            //Update the function parsing in
            currentfunction = currentToken.image;
            ft.func_name = currentfunction;
            consume(ID);

            consume(LEFTPAREN);
            int space_para = parameterList(); //empty
            consume(RIGHTPAREN);
            consume(LEFTBRACE);
            int space_local = localDeclarations();
            statementList();
            returnStatement();
            consume(RIGHTBRACE);

            st.enterFunc(currentfunction, ft);
            //Reset function
            ft.reset();
    }

    private int parameterList()
    {
        int space;
        switch (currentToken.kind)
        {
            case INT:
                parameter();
                parameterTail();
                space = -4*(ft.argLength()+2);
                emitInstruction("addi", "$sp","$sp",space+"");
                for(int i=0;i<ft.args_num;i++)
                {
                    emitInstruction("sw","$a"+i,(4*(ft.argLength()+1-i))+"($sp)");
                }
                emitInstruction("sw", "$ra","4($sp)");
                emitInstruction("sw", "$fp","0($sp)");
                emitInstruction("move", "$fp","$sp");
                return space;
            default:
                space = -8;
                emitInstruction("addi", "$sp","$sp",space+"");
                emitInstruction("sw", "$ra","4($sp)");
                emitInstruction("sw", "$fp","0($sp)");
                emitInstruction("move", "$fp","$sp");
                return -8;
        }
    }

    private int localDeclarations()
    {
        switch (currentToken.kind)
        {
            case INT:
                consume(INT);
                ft.varEnter(currentToken.image);
                consume(ID);
                localTail();
                int space = (-4)*ft.varLength();
                //Expand memory space according to the declaration
                emitInstruction("addi", "$sp","$sp",""+space);
                consume(SEMICOLON);
                return space;
            default:
                return 0;
        }
    }

    private void localTail()
    {
        switch (currentToken.kind)
        {
            case COMMA:
                consume(COMMA);
                ft.varEnter(currentToken.image);
                consume(ID);
                localTail();
            default:
                break;
        }
    }
    private void parameter()
    {
        consume(INT);
        //enter the args into the symtable of this function
        ft.argEnter(currentToken.image);
        consume(ID);
    }

    private void parameterTail()
    {
        switch (currentToken.kind)
        {
            case COMMA:
                consume(COMMA);
                parameter();
                parameterTail();
                break;
            default:
                break;
        }
    }
    private void program()
    {
        outFile.println("\t.text");
        emitInstruction("move", "$fp","$sp");
        emitInstruction("jal", "main");
        emitInstruction("j", "exit");
        programUnitList();
        if (currentToken.kind != EOF)
            throw genEx("Expecting <EOF>");
        outFile.println("exit:");
        dataSegment();
    }

    private void statementList()
    {
        switch(currentToken.kind)
        {
            case ID:
            case WHILE:
            case PRINTLN:
            case IF:
            case CAL:
            case RETURN:
            case LEFTBRACE:
                statement();
                statementList();
                break;
            case EOF:
            case RIGHTBRACE:
                break;
            default:
                throw genEx("Expecting statement or <EOF>");
        }
    }
    private void statement()
    {
        switch(currentToken.kind)
        {
            case ID:
                assignmentStatement();
                break;
            case PRINTLN:
                printlnStatement();
                break;
            case WHILE:
                whileStatement();
                break;
            case LEFTBRACE:
                compoundStatement();
                break;
            case IF:
                ifStatement();
                break;
            case RETURN:
                returnStatement();
                break;
            case CAL:
                functionCall();
                break;
            default:
                throw genEx("Expecting statement");
        }
    }

    private boolean returnStatement()
    {
        switch (currentToken.kind)
        {
            case RETURN:
                consume(RETURN);
                String reg_result = isNeedRegister(expr());
                emitInstruction("move", "$v0",reg_result);
                consume(SEMICOLON);
                emitInstruction("lw", "$ra","4($fp)");
                emitInstruction("lw", "$fp","0($fp)");
                emitInstruction("addi", "$sp","$sp",""+ft.getSpace());
                emitInstruction("jr", "$ra");
                return true;
            default:
                return false;
        }
    }

    private void functionCall()
    {

        consume(CAL);
        String func_name = currentToken.image;
        consume(ID);
        consume(LEFTPAREN);

        argumentList();

        consume(RIGHTPAREN);
        //consume(SEMICOLON);

        //argumentList will also consume registers
        int reg_t = this.registercount;
        int reg_s = this.registerS_count;
        int save_reg = (-4)*(reg_s+reg_t);

        System.out.println("reg"+this.registercount);
        System.out.println("reg"+this.registerS_count);

        emitInstruction("addi", "$sp","$sp",""+save_reg);

        int pointer = -save_reg-4;
        for(int i=reg_s-1;i>=0;i--)
        {
            emitInstruction("sw", "$s"+i,pointer+"($sp)");
            pointer-=4;
        }
        for(int i=reg_t-1;i>=0;i--)
        {
            emitInstruction("sw", "$t"+i,pointer+"($sp)");
            pointer-=4;
        }

        resetRegister();

        emitInstruction("jal",func_name);

        pointer = 0;

        for(int i=0;i<reg_t;i++)
        {
            emitInstruction("lw", "$t"+i,pointer+"($sp)");
            pointer+=4;
        }
        for(int i=0;i<reg_s;i++)
        {
            emitInstruction("lw", "$s"+i,pointer+"($sp)");
            pointer+=4;
        }emitInstruction("addi", "$sp","$sp",""+(-1)*save_reg);


    }
    /**
     * @ArgumentList
     * */
    private void argumentList()
    {
        switch (currentToken.kind)
        {
            case RIGHTPAREN:
                break;
            default:
                String reg = registerA_Available();
                String reg_result = isNeedRegister(expr());
                emitInstruction("move", reg,reg_result);
                argtail();
                break;
        }
    }

    private void argtail()
    {
        switch (currentToken.kind)
        {
            case COMMA:
                consume(COMMA);
                String reg = registerA_Available();
                String reg_result = isNeedRegister(expr());
                emitInstruction("move", reg,reg_result);
                argtail();
                break;
            default:
                break;
        }
    }

    private void loadVariable(String reg,String var)
    {
        //Search this symbol in symtable
        int index = ft.argLocate(var);
        //Defined in args list
        if(index >= 0)
        {
            /**@marked
             * The push sequence and index sequence are inverse
             * So we need some tricks
             * */
            emitInstruction("lw",reg, (4*(ft.argLength()+1)-index*4)+"($fp)");
        }
        else if(index < 0)
        {
            index = ft.varLocate(var);
            if(index >= 0)emitInstruction("lw",reg, (4*(ft.varLength()-1)-index*4)+"($sp)");
            System.out.println("here:"+index);
        }
        if(index < 0) throw genEx(var+" not defined");
    }

    private void saveVariable(String reg,String var)
    {
        int index = ft.argLocate(var);
        //Defined in args list
        if(index >= 0)
        {
            emitInstruction("sw",reg, (4*(ft.argLength()+1)-index*4)+"($fp)");
        }
        else if(index < 0)
        {
            index = ft.varLocate(var);
            emitInstruction("sw",reg, (4*(ft.varLength()-1)-index*4)+"($sp)");
        }
        if(index < 0) throw genEx(var+" not defined");
    }

    private void assignmentStatement()
    {

        Token t = currentToken;
        String left_op = t.image; //identifier on the left
        consume(ID);
        String reg = registerAvailable();

        loadVariable(reg,left_op);

        st.enter(t.image);
        consume(ASSIGN);
        String temp = expr();
        //Strx are not allowed to use
        try{
            if(StringIdentifiers.contains(left_op)) //Every time before we use, we first delete it
            {
                this.StringIdentifiers.remove(left_op);
            }
            if(temp.substring(0, 3).equals("Str") && !StringIdentifiers.contains(left_op))
            {
                this.StringIdentifiers.add(left_op);
            }
        }catch (Exception e){}

        String reg_temp = isNeedRegister(temp);
        emitInstruction("move", reg,reg_temp);

        saveVariable(reg,t.image);

        resetRegister();
        System.out.println(temp);
        consume(SEMICOLON);
    }

    private void printlnStatement()
    {
        String temp;
        consume(PRINTLN);
        consume(LEFTPAREN);
        outFile.println("#println Statement");
        temp = expr();
        try
        {
            if(temp.substring(0, 3).equals("Str"))
            {
                String reg_temp = isNeedRegister(temp);
                emitInstruction("li","$v0","4");
                emitInstruction("move", "$a0",reg_temp);
                outFile.println("syscall");
                consume(RIGHTPAREN);
                consume(SEMICOLON);
                return;
            }
        }
        catch (Exception e){
            System.out.println("Exception happen 1");
        }

        String reg_temp = isNeedRegister(temp);

        if(this.StringIdentifiers.contains(temp))
        {
            emitInstruction("li", "$v0","4");
        }
        else
        {
            emitInstruction("li", "$v0","1");
        }

        emitInstruction("move","$a0",reg_temp);
        outFile.println("syscall");
        consume(RIGHTPAREN);
        consume(SEMICOLON);
    }

    private void compoundStatement()
    {
        consume(LEFTBRACE);
        statementList();
        consume(RIGHTBRACE);
    }

    private void whileStatement()
    {
        String judge_point = identifierAvailable();
        String judge_exit = identifierAvailable();
        String judge;
        outFile.println(judge_point+":");
        consume(WHILE);
        consume(LEFTPAREN);
        judge = expr();

        String reg_judge = isNeedRegister(judge);

        outFile.println("beq"+"\t$zero"+",\t"+reg_judge+",\t"+judge_exit);
        consume(RIGHTPAREN);
        statement();
        outFile.println("j"+"\t"+judge_point);
        outFile.println(judge_exit+":");
        System.out.println("Successfully parse while");
    }

    //-----------------------------------------
    private void ifStatement() {
        String judge_else = identifierAvailable();
        String judge_exit = identifierAvailable();
        consume(IF);
        consume(LEFTPAREN);
        String judge = expr();
        String reg_judge = isNeedRegister(judge);
        outFile.println("beq"+"\t$zero"+",\t"+reg_judge+",\t"+judge_else);
        consume(RIGHTPAREN);
        statement();
        outFile.println("j"+"\t"+judge_exit);
        elsePart(judge_else);
        outFile.println(judge_exit+":");
    }

    //-----------------------------------------
    private void elsePart(String judge_else) {
        outFile.println(judge_else+":");
        switch (currentToken.kind) {
            case ELSE:
                consume(ELSE);
                statement();
                break;
            default:
        }
    }

    private String expr()
    {
        String term_val,expr_val,termlist_syn;
        term_val = term();
        termlist_syn = termList(term_val);
        expr_val = termlist_syn;
        return expr_val;
    }

    /**
     *Allocate register for data in memory or immediate data
     * */
    private String isNeedRegister(String term)
    {
        System.out.println(term);
        try
        {
            //If it's string immediate, use la
            if(term.substring(0,3).equals("Str"))
            {
                String reg = registerAvailable();
                emitInstruction("la", reg,term);
                return reg;
            }
        }
        catch (Exception e){}
        if(term.charAt(0) == '$')
        {
            return term;
        }else if(Character.isDigit(term.charAt(0)))
        {
            String reg = registerAvailable();
            emitInstruction("li", reg,term);
            return reg;
        }
        else
        {
            String reg = registerAvailable();
            loadVariable(reg, term);
            return reg;
        }
    }

    private String termList(String inh)
    {
        String term_val,termlist_syn;
        String reg_inh;
        String reg_term_val;
        String reg_plus;
        String reg_result,reg_equ;
        switch(currentToken.kind)
        {
            case PLUS:
                consume(PLUS);
                term_val = term();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(term_val);
                reg_result = registerAvailable();

                outFile.println("add"+"\t"+reg_result +",\t" + reg_term_val +",\t"+reg_inh);

                termlist_syn = termList(reg_result);
                break;

            case MINUS:
                consume(MINUS);
                term_val = term();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(term_val);
                reg_result = registerAvailable();

                outFile.println("sub"+"\t"+reg_result +",\t" + reg_inh +",\t"+reg_term_val);

                termlist_syn = termList(reg_result);
                break;

            case EQUAL:
                consume(EQUAL);
                termlist_syn = expr();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(termlist_syn);
                reg_result = registerS_Available();
                reg_equ = registerS_Available();

                emitInstruction("seq", reg_result,reg_inh,reg_term_val);

                termlist_syn = reg_result;

                resetRegister();
                System.out.println("EQUAL, Diter: "+inh+" "+termlist_syn);
                break;

            case GREATER_EQUAL_THAN:
                consume(GREATER_EQUAL_THAN);
                termlist_syn = expr();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(termlist_syn);
                reg_result = registerS_Available();
                reg_equ = registerS_Available();

                emitInstruction("sge", reg_result,reg_inh,reg_term_val);

                termlist_syn = reg_result;
                resetRegister();

                System.out.println("GREATER_EQUAL_THAN, compare: "+reg_inh+" "+reg_term_val);
                break;

            case SMALLER_EQUAL_THAN:
                consume(SMALLER_EQUAL_THAN);
                termlist_syn = expr();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(termlist_syn);
                reg_result = registerS_Available();
                reg_equ = registerS_Available();

                emitInstruction("sle", reg_result,reg_inh,reg_term_val);

                termlist_syn = reg_result;
                resetRegister();

                System.out.println("SMALLER_EQUAL_THAN, compare: "+inh+" "+termlist_syn);
                break;

            case GREATER_THAN:
                consume(GREATER_THAN);
                termlist_syn = expr();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(termlist_syn);
                reg_result = registerS_Available();

                emitInstruction("sgt", reg_result,reg_inh,reg_term_val);

                termlist_syn = reg_result;
                resetRegister();
                System.out.println("GREATER_THAN, compare: "+inh+" "+termlist_syn);
                break;

            case SMALLER_THAN:
                consume(SMALLER_THAN);
                termlist_syn = expr();

                reg_inh = isNeedRegister(inh);
                reg_term_val = isNeedRegister(termlist_syn);
                reg_result = registerS_Available();

                emitInstruction("slt",reg_result, reg_inh,reg_term_val);

                termlist_syn = reg_result;
                resetRegister();
                System.out.println("SMALLER_THAN, compare: "+inh+" "+termlist_syn);
                break;
            case AND:
            case OR:
                booleanExpression();
                termlist_syn = inh;//!!!!!!!!!!!!!!!!wrong
                break;
            case RIGHTPAREN:
            case SEMICOLON:
                termlist_syn = inh;
                break;
            default:
                throw genEx("Expecting \"+\", \")\", or \";\"");
        }
        return termlist_syn;
    }

    private void booleanExpression()
    {
        switch (currentToken.kind)
        {
            case AND:
                consume(AND);
                expr();
                booleanExpression();
                break;
            case OR:
                consume(OR);
                expr();
                booleanExpression();
                break;
            default:
                break;
        }
    }

    private String term()
    {
        switch(currentToken.kind)
        {
            case CAL:
                functionCall();
                return "$v0";
            default:
                String factorlist_inh,term_val,factorlist_syn;
                factorlist_inh = factor();
                factorlist_syn = factorList(factorlist_inh);
                term_val = factorlist_syn;
                return term_val;
        }

    }

    private String factorList(String inh)
    {
        String factor_val,factorlist_syn;
        switch(currentToken.kind)
        {
            case TIMES:
                consume(TIMES);
                factor_val = factor();

                String reg_inh = isNeedRegister(inh);
                String reg_factor_val = isNeedRegister(factor_val);

                String newidentifier = registerAvailable();
                outFile.println("mult"+"\t"+reg_inh+",\t"+reg_factor_val);
                outFile.println("mflo\t"+newidentifier);

                factorlist_syn = factorList(newidentifier);
                break;
            case DIVIDE:
                consume(DIVIDE);
                factor_val = factor();

                String reg_inh_div = isNeedRegister(inh);
                String reg_factor_val_div = isNeedRegister(factor_val);
                String newidentifier_div = registerAvailable();

                outFile.println("div"+"\t"+reg_inh_div+",\t"+reg_factor_val_div);
                outFile.println("mflo\t"+newidentifier_div);

                factorlist_syn = factorList(newidentifier_div);
                break;
            case PLUS:
            case MINUS:
            case RIGHTPAREN:
            case SEMICOLON:
            case EQUAL:
            case GREATER_EQUAL_THAN:
            case SMALLER_EQUAL_THAN:
            case GREATER_THAN:
            case SMALLER_THAN:
                factorlist_syn = inh;
                break;
            case AND:
            case OR: //~!!!!!!!!!!!!!!!!!!!!!!!!!wrong
                factorlist_syn = inh;
                break;
            default:
                throw genEx("Expecting op, \")\", or \";\"");
        }
        return factorlist_syn;
    }

    private String factor()
    {
        Token t;
        String factor_val;
        switch(currentToken.kind)
        {
            case UNSIGNED:
                t = currentToken;
                consume(UNSIGNED);
                factor_val = t.image;
                break;
            case PLUS:
                consume(PLUS);
                t = currentToken;
                consume(UNSIGNED);
                factor_val = t.image;
                break;
            case MINUS:
                consume(MINUS);
                t = currentToken;
                consume(UNSIGNED);
                factor_val = "-"+t.image;
                break;
            case ID:
                t = currentToken;
                consume(ID);
                st.enter(t.image);
                factor_val = registerAvailable();
                loadVariable(factor_val, t.image);
                break;

            case STRING:
                t = currentToken;
                consume(STRING);
                factor_val = sm.enter(t.image); //Automatic another line
                break;
            case LEFTPAREN:
                consume(LEFTPAREN);
                factor_val = expr();
                consume(RIGHTPAREN);
                break;
            case CAL:
                functionCall();
                factor_val = "$v0";
                break;
            default:
                throw genEx("Expecting factor");
        }
        return factor_val;
    }

    /**
     * Generate ".data" segment in MIPS instructions
     * Based on symbol table we have
     * */
    private void dataSegment()
    {
        outFile.println("\t"+".data");
        for(int i=0;i<sm.getSize();i++)
        {
            String str = sm.getItem(i);
            outFile.println("Str"+i+":\t"+".asciiz\t"+str);
        }
    }
}

