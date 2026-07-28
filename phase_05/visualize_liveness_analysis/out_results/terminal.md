```bash
> Task :run

ExplodedBlock[0](entry:< Application, LMain, main([Ljava/lang/String;)V >)
null
  LIVE-IN:  { }
  LIVE-OUT: { }


ExplodedBlock[9](exit:< Application, LMain, main([Ljava/lang/String;)V >)
null
  LIVE-IN:  { }
  LIVE-OUT: { }


ExplodedBlock[1](original:BB[SSA:0..0]1 - Main.main([Ljava/lang/String;)V)
4 = invokestatic < Application, LMain, compute()I > @0 exception:3
  SOURCE:   line 3: int alive = compute();
  LIVE-IN:  { }
  LIVE-OUT: { alive }


ExplodedBlock[3](original:BB[SSA:1..2]2 - Main.main([Ljava/lang/String;)V)
6 = invokestatic < Application, LMain, compute2()I > @4 exception:5
  SOURCE:   line 4: int dead = compute2();
  LIVE-IN:  { alive }
  LIVE-OUT: { alive }


ExplodedBlock[5](original:BB[SSA:3..6]3 - Main.main([Ljava/lang/String;)V)
7 = getstatic < Application, Ljava/lang/System, out, <Application,Ljava/io/PrintStream> >
  SOURCE:   line 5: System.out.println(alive);
  LIVE-IN:  { alive }
  LIVE-OUT: { alive }


ExplodedBlock[7](original:BB[SSA:3..6]3 - Main.main([Ljava/lang/String;)V)
invokevirtual < Application, Ljava/io/PrintStream, println(I)V > 7,4 @12 exception:8
  SOURCE:   line 5: System.out.println(alive);
  LIVE-IN:  { alive }
  LIVE-OUT: { }


ExplodedBlock[8](original:BB[SSA:7..7]4 - Main.main([Ljava/lang/String;)V)
return
  SOURCE:   line 6: }
  LIVE-IN:  { }
  LIVE-OUT: { }

PDF written to /Users/stamp/Uni-Ku-4/WALA_Start_test/out-class/cfg-out/Liveness_Exploded_CFG.pdf
PDF written to /Users/stamp/Uni-Ku-4/WALA_Start_test/out-class/cfg-out/Liveness_Source_Exploded_CFG.pdf
PDF written to /Users/stamp/Uni-Ku-4/WALA_Start_test/out-class/cfg-out/Liveness_Source_Merged_CFG.pdf
```