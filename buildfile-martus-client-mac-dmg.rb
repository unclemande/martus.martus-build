name = 'martus-client-mac-dmg'

define name, :layout=>create_layout_with_source_as_source('.') do
	project.group = 'org.martus'
  project.version = ENV['RELEASE_IDENTIFIER']
  input_build_number = ENV['INPUT_BUILD_NUMBER']

	build do
    hudson_job_dir = "/var/lib/hudson/jobs/martus-client-unsigned"
    dmg_mount_point = File.join(hudson_job_dir, "mounts/dmg")
    dmg_file = File.join(hudson_job_dir, "Martus.dmg")

    production_zip_contents_dir = get_extracted_production_zip_contents_directory
	
    dmg_contents_dir = _("temp", "dmgcontents")
    create_empty_directory(dmg_contents_dir)
    
    # COPY FILES FROM THE PRODUCTION ZIP
    # NOTE: The jars themselves are copied by jarbundler
    docs_dir = File.join(dmg_contents_dir, "MartusDocumentation")
    FileUtils::mkdir_p(docs_dir)
    readmes = Dir[File.join(production_zip_contents_dir, "*.txt")]
    FileUtils::cp(readmes, docs_dir)
    pdfs = Dir[File.join(production_zip_contents_dir, "Documents/*.pdf")]
    FileUtils::cp(pdfs, docs_dir)
    
    licenses_dir = File.join(production_zip_contents_dir, "ThirdParty/Licenses")
    FileUtils::cp_r(licenses_dir, docs_dir)
    
    dmg_fonts_dir = File.join(production_zip_contents_dir, "Fonts")
    FileUtils::cp_r(dmg_fonts_dir, dmg_contents_dir)
    dmg_fonts_cvs_dir = File.join(dmg_fonts_dir, "CVS")
    if(File.exists?(dmg_fonts_cvs_dir))
      FileUtils::rm_r(dmg_fonts_cvs_dir)
    end

    # COPY MAC-SPECIFIC FILES NOT IN THE ZIP
    mac_readme = _("martus", 'BuildFiles', 'Documents', 'client', 'Mac-install-README.txt')
		FileUtils::cp([mac_readme], dmg_contents_dir)

		# NOTE: This does not appear to be working. We need to learn more 
		# about mac app icons before spending more time on it.
		mac_icon_file = _("martus", 'BuildFiles', 'ProgramFiles', 'Martus-Mac')

    buildfile_option = "-buildfile martus/martus-client-mac-dmg.ant.xml"
    properties = ""
    properties << " -Dmac.app.name=Martus"
    properties << " -Dshort.app.name=Martus"
    properties << " -Dversion.full=#{version}"
    properties << " -Dversion.build=#{input_build_number}"
    properties << " -Dmain.class=org.martus.client.swingui.Martus"
    properties << " -Dmac.icon.file=#{mac_icon_file}"

    properties << " -Dinstaller.mac=BuildFiles/Mac/" #parent of JavaApplicationStub
    properties << " -Dapp.dir=#{production_zip_contents_dir}"
    properties << " -Dvm.options=-Xbootclasspath/p:Contents/Resources/Java/ThirdParty/bc-jce.jar"

    properties << " -Ddist.mactree=#{dmg_contents_dir}" #can be temp
    properties << " -Ddmg.dest.dir=#{_('dist')}"
    properties << " -Drawdmgfile=#{dmg_file}"
    properties << " -Ddmgmount=#{dmg_mount_point}"
    properties << " -Ddmg.size.megs=40"
	
    ant = "ant #{buildfile_option} macdmgfile #{properties}"
    puts `#{ant}`
    if $CHILD_STATUS != 0
      raise "Failed in dmg ant script #{$CHILD_STATUS}"
    end
    
    destination_filename = "MartusClient-#{project.version}-#{input_build_number}.dmg"
    destination = _(:target, destination_filename)
    FileUtils.cp dmg_file, destination
    create_sha_files(destination)
	end

	def create_empty_directory(dir)
    if(File.exists?(dir))
      FileUtils::rm_r(dir)
    end
    FileUtils::mkdir_p(dir)
	end
	
	def get_extracted_production_zip_contents_directory
    production_zipfile = project('martus-client-linux-zip').package.to_s

    raw_production_zip_contents_dir = _("temp", "production")
    create_empty_directory(raw_production_zip_contents_dir)
    
    FileUtils::mkdir_p(raw_production_zip_contents_dir)
    unzip_file(production_zipfile, raw_production_zip_contents_dir)
    production_zip_contents_dir = File.join(raw_production_zip_contents_dir, "MartusClient-#{project.version}")
    return production_zip_contents_dir 
	end
	  
end
