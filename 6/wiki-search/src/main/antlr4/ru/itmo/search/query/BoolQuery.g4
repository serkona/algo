grammar BoolQuery;

// Entry point.
query   : orExpr EOF ;

// Precedence, lowest to highest: OR < AND < NOT < proximity(ADJ/NEAR) < primary.
orExpr  : andExpr (OR andExpr)* ;
andExpr : notExpr (AND notExpr)* ;
notExpr : NOT notExpr        # notExprNeg
        | proxExpr           # notExprPass
        ;
proxExpr: primary (proxOp primary)* ;
proxOp  : ADJ slop?          # adjOp
        | NEAR slop?         # nearOp
        ;
slop    : SLASH INT ;
primary : LPAREN orExpr RPAREN   # parenExpr
        | QUOTED                 # phraseExpr
        | term                   # termExpr
        ;
term    : TERM | INT ;

// --- Lexer ---
AND   : [Aa][Nn][Dd] | '&&' ;
OR    : [Oo][Rr] | '||' ;
NOT   : [Nn][Oo][Tt] | '!' | '-' ;
ADJ   : [Aa][Dd][Jj] ;
NEAR  : [Nn][Ee][Aa][Rr] ;
LPAREN: '(' ;
RPAREN: ')' ;
SLASH : '/' ;
INT   : [0-9]+ ;
QUOTED: '"' ~["]* '"' ;
TERM  : [\p{Alnum}_]+ ;
WS    : [ \t\r\n]+ -> skip ;
