#!/usr/bin/env python3.4

import getpass
import importlib
from pathlib import Path
import shutil
import tempfile


# TODO: Generic configurable setups: environment setup (wrkdir/config), run setup (wrkdir/run), process setup (tmp/procs)
# TODO: Configuration phases and precedence? defaults in script < base file < prot file < ... < arguments
#       Which protfile should be used could depend on settings in the base file and could be given as argument.
class ExecutionEnvironment:
    def __init__(self, prjdir, ctrlscript):
        self.projectdir    = prjdir
        self.controlscript = ctrlscript
        self.builddir      = self.projectdir / 'build' / 'classes'
        self.depsdir       = self.projectdir / 'build' / 'deps'
        self.libdir        = self.projectdir / 'libs'
        self.scriptdir     = self.projectdir / 'scripts'
        self.configdir     = self.projectdir / 'config'
        self.workingdir    = self.projectdir / 'wrkdir'
        self.setupdir      = self.workingdir / 'config'
        self.rundir        = self.workingdir / 'run'
        self.resultsdir    = self.workingdir / 'results'
        self.run_configdir = self.rundir / 'config'
        self.run_logdir    = self.rundir / 'logs'
        self.run_resdir    = self.rundir / 'results'

    def create_rundir(self):
        self.rundir.mkdir( parents=True )
        self.run_configdir.mkdir( parents=True )
        self.run_logdir.mkdir( parents=True )
        self.run_resdir.mkdir( parents=True )

    def cleanup_rundir(self):
        shutil.rmtree( str( self.rundir ) )

    def reset_run(self):
        self._cleanup_dir( self.run_logdir )
        self._cleanup_dir( self.run_resdir )

    def _cleanup_dir(self, path):
        if path.exists():
            for child in path.iterdir():
                print( 'Remove {}'.format( child ) )
                if child.is_dir():
                    shutil.rmtree( str( child ) )
                else:
                    child.unlink()

# TODO: What was the reason that the system is part of the environment and not the other way around?
class ReptorExecutionEnvironment(ExecutionEnvironment):
    def __init__(self, prjdir, ctrlscript):
        super().__init__( prjdir, ctrlscript )

        self.system = None

        self.trinxdir      = self.projectdir / 'build' / 'trinx'
        self.processdir    = Path( tempfile.gettempdir() ) / ( 'reptor-procs-'+getpass.getuser() )

        self.netenv_setupfile     = self.setupdir / 'netenv.py'
        self.logging_configfile   = self.setupdir / 'logback.xml'

        self.base_configfile      = self.configdir / 'base.cfg'
        self.protocol_configfile  = self.configdir / 'hybster.cfg'

        self.replica_scheduler_config_name = '1'
        self.replica_scheduler_configfile  = self.configdir / 'rep-sched-S1.cfg'
        self.client_scheduler_config_name  = '10'
        self.client_scheduler_configfile   = self.configdir / 'cli-sched-S10.cfg'

        self.run_configfile  = self.run_configdir / 'system.cfg'
        self.run_controlfile = self.rundir / 'control.txt'

    # TODO: Move settings for process groups and single processes to the corresponding objects?
    #       Central vs. distributed settings
        self.replicas_maximum_starttime = 600
        self.clients_maximum_starttime  = 600

        self.netenv = None
        self.load_netenv_setup()

    def reset_run(self):
        super().reset_run()

        if self.run_controlfile.exists():
            print( 'Remove {}'.format( self.run_controlfile ) )
            self.run_controlfile.unlink()

    def replica_logfile(self, repno):
        return self.run_logdir / 'replica{}.log'.format( repno )

    def client_logfile(self, clino):
        return self.run_logdir / 'client{}-stdout.log'.format( clino )

    def monitor_logfile(self, proc):
        return self.run_logdir / '{}.log'.format( proc.name )

    def replica_processdir(self, repno):
        return self.processdir / 'replica{}'.format( repno )

    def client_processdir(self, clino):
        return self.processdir / 'client{}'.format( clino )

    def monitor_processdir(self, proc):
        return self.processdir / proc.name

    def netenv_configfile(self, netenvname):
        return self.configdir / 'netenvs' / 'netenv_{}.py'.format( netenvname )

    def logging_configfile_by_name(self, loggingname):
        return self.configdir / 'logging' / 'logback_{}.xml'.format( loggingname )

    def set_protocol_config_by_name(self, protname):
        self.protocol_configfile = self.configdir / '{}.cfg'.format( protname )

    def set_replica_scheduler_config_by_name(self, schedname):
        self.replica_scheduler_config_name = schedname
        self.replica_scheduler_configfile  = self.configdir / 'rep-sched-S{}.cfg'.format( schedname )

        if self.system:
            self.system.global_settings.replica_scheduler_config = schedname

    def set_client_scheduler_config_by_name(self, schedname):
        self.client_scheduler_config_name = schedname
        self.client_scheduler_configfile  = self.configdir / 'cli-sched-S{}.cfg'.format( schedname )

        if self.system:
            self.system.global_settings.client_scheduler_config = schedname

    def setup_logging_by_name(self, loggingname):
        configfile = self.logging_configfile_by_name( loggingname )

        print( 'New default logging config: {}'.format( configfile ) )

        if not self.logging_configfile.parent.exists():
            self.logging_configfile.parent.mkdir( parents=True )

        shutil.copyfile( str( configfile ), str( self.logging_configfile ) )

    def setup_netenv_by_name(self, netenvname):
        configfile = self.netenv_configfile( netenvname )

        print( 'New default network config: {}'.format( configfile ) )

        if not self.netenv_setupfile.parent.exists():
            self.netenv_setupfile.parent.mkdir( parents=True )

        shutil.copyfile( str( configfile ), str( self.netenv_setupfile ) )
        self.load_netenv_setup()

    def load_netenv_setup(self):
        if not self.netenv_setupfile.exists():
            return None

        netenvmod = importlib.machinery.SourceFileLoader( 'netenv_setup', str( self.netenv_setupfile ) ).load_module()
        self.netenv = netenvmod.load_netenv()

        return self.netenv

    def load_netenv_by_name(self, netenvname):
        self.netenv = importlib.import_module( 'netenv_' + netenvname ).load_netenv()

    def set_system(self, system):
        self.system = system

        self.system.global_settings.replica_scheduler_config = self.replica_scheduler_config_name
        self.system.global_settings.client_scheduler_config  = self.client_scheduler_config_name

    def setup_run(self):
        if self.rundir.exists():
            self.cleanup_rundir()

        self.create_rundir()

        self.system.assign_concrete_objects( self.netenv.allocate( self.system ) )
        self.system.setup_run( self )

    def load_base_config(self):
        self.system.load_config( self.base_configfile )

    def load_protocol_config(self):
        self.system.load_config( self.protocol_configfile )

    def load_scheduler_config(self):
        self.system.load_config( self.replica_scheduler_configfile )
        self.system.load_config( self.client_scheduler_configfile )

    def load_run(self):
        self.system.load_current_run( self )

    def cleanup_run(self):
        if self.rundir.exists():
            print( 'Cleanup {}'.format( self.rundir ) )
            self.cleanup_rundir()

    def cleanup_entenv(self):
        if self.netenv_setupfile.exists():
            print( 'Remove {}'.format( self.netenv_setupfile ) )
            self.netenv_setupfile.unlink()
