task :lint do
  require 'yaml'

  d = Dir["./**/*.yaml"]
  d.each do |file|
    begin
      puts "checking : #{file}"
      f = YAML.load_file(file)
    rescue Exception
      puts "failed to read #{file}: #{$!}"
    end
  end
end
