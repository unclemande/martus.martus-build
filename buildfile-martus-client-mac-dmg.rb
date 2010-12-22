name = 'martus-client-mac-dmg'

define name, :layout=>create_layout_with_source_as_source(name) do
	project.group = 'org.martus'
	project.version = '1'

    version = "3.5.1"
    timestamp = "20101116.1964"
    hudson_job_dir = "/var/lib/hudson/jobs/martus-client-unsigned"
    dmg_mount_point = File.join(hudson_job_dir, "mounts/dmg")
    dmg_file = File.join(hudson_job_dir, "Martus.dmg")
    production_zipfile_dir = File.join(hudson_job_dir, "artifacts")
    production_zip_name = "/MartusClient-#{version}-#{timestamp}-MacLinux.zip"
    production_zipfile = File.join(production_zipfile_dir, production_zip_name)

    tmpdir = Dir.mktmpdir
    puts "Using temp dir: #{tmpdir}"
    dmg_contents_dir = File.join(tmpdir, "dmgcontents")
    raw_production_zip_contents_dir = File.join(tmpdir, "production")
    Dir.mkdir(dmg_contents_dir)
    Dir.mkdir(raw_production_zip_contents_dir)

    unzip_file(production_zipfile, raw_production_zip_contents_dir)
    production_zip_contents_dir = File.join(raw_production_zip_contents_dir, "MartusClient-#{version}")
#puts production_zip_contents_dir
#puts "press enter"
#$stdin.gets

    buildfile_option = "-buildfile martus-client-mac-dmg.ant.xml"
    properties = ""
    properties << " -Dmac.app.name=Martus"
    properties << " -Dshort.app.name=Martus"
    properties << " -Dversion.full=#{version}"
    properties << " -Dversion.timestamp=#{timestamp}"
    properties << " -Dmain.class=org.martus.swingui.martus"

    properties << " -Dinstaller.mac=BuildFiles/Mac/" #parent of JavaApplicationStub
    properties << " -Dapp.dir=#{production_zip_contents_dir}"
    properties << " -Dvm.options=-Xbootclasspath/p:/Library/Java/Extensions/bc-jce.jar"

    properties << " -Ddist.mactree=#{dmg_contents_dir}" #can be temp
    properties << " -Ddmg.dest.dir=#{_('dist')}"
    properties << " -Drawdmgfile=#{dmg_file}"
    properties << " -Ddmgmount=#{dmg_mount_point}"
    properties << " -Ddmg.size.megs=40"

    ant = "/opt/java/tools/ant/bin/ant #{buildfile_option} macdmgfile #{properties}"
puts ant
    `#{ant}`
    if $CHILD_STATUS != 0
        raise "Failed in dmg ant script #{$CHILD_STATUS}"
    end
    
end