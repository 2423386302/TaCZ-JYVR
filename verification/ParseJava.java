import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class ParseJava {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  if(compiler==null) throw new IllegalStateException("JDK 17 required");
  DiagnosticCollector<JavaFileObject> ds=new DiagnosticCollector<>();
  try(var fm=compiler.getStandardFileManager(ds,null,null);var paths=Files.walk(Path.of(args[0]))) {
   var files=paths.filter(p->p.toString().endsWith(".java")).map(Path::toFile).toList();
   JavacTask task=(JavacTask)compiler.getTask(null,fm,ds,List.of("-proc:none","--release","17"),null,fm.getJavaFileObjectsFromFiles(files));
   task.parse();
   long errors=ds.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).peek(System.out::println).count();
   System.out.println("Java syntax parsing: "+files.size()+" files, "+errors+" errors. Not a type check or Forge build.");
   if(errors>0) System.exit(1);
  }
 }
}
